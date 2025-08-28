package com.sttweb.sttweb.logging;

import com.sttweb.sttweb.dto.TactivitylogDto;
import com.sttweb.sttweb.dto.TmemberDto.Info;
import com.sttweb.sttweb.dto.TmemberDto.LoginRequest;
import com.sttweb.sttweb.dto.TmemberDto.SignupRequest;
import com.sttweb.sttweb.jwt.JwtTokenProvider;
import com.sttweb.sttweb.service.TactivitylogService;
import com.sttweb.sttweb.service.TbranchService;
import com.sttweb.sttweb.service.TmemberService;
import com.sttweb.sttweb.service.TrecordService;
import jakarta.servlet.http.HttpServletRequest;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Enumeration;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
@RequiredArgsConstructor
public class ActivityLogAspect {

  private static final Logger            log = LoggerFactory.getLogger(ActivityLogAspect.class);
  private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private final SpelExpressionParser    parser   = new SpelExpressionParser();
  private final TemplateParserContext   template = new TemplateParserContext();
  private final ParameterNameDiscoverer pd       = new DefaultParameterNameDiscoverer();

  private final TactivitylogService logSvc;
  private final TmemberService      memberSvc;
  private final TbranchService      branchSvc;
  private final TrecordService      recordSvc;
  private final JwtTokenProvider    jwt;
  private final BeanFactory         beanFactory;

  @Around("@annotation(logActivity)")
  public Object around(ProceedingJoinPoint jp, LogActivity logActivity) throws Throwable {

    Object result = jp.proceed();

    ServletRequestAttributes sa = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    if (sa == null) return result;
    HttpServletRequest req = sa.getRequest();

    // operator 식별
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    String operatorUserId = "";
    if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
      operatorUserId = auth.getName();
    }
    for (Object a : jp.getArgs()) {
      if (operatorUserId.isEmpty() && a instanceof LoginRequest lr)  operatorUserId = lr.getUserId();
      if (operatorUserId.isEmpty() && a instanceof SignupRequest sr) operatorUserId = sr.getUserId();
    }
    if (operatorUserId.isEmpty()) {
      String rt = req.getHeader("X-ReAuth-Token");
      if (rt != null && jwt.validateReAuthToken(rt)) operatorUserId = jwt.getUserId(rt);
    }
    if (operatorUserId.isEmpty()) {
      String ah = req.getHeader(HttpHeaders.AUTHORIZATION);
      if (ah != null && ah.startsWith("Bearer ")) {
        String tok = ah.substring(7).trim();
        if (jwt.validateToken(tok)) operatorUserId = jwt.getUserId(tok);
      }
    }

    Info me = operatorUserId.isBlank() ? null : memberSvc.getMyInfoByUserId(operatorUserId);
    int    opSeq    = me != null ? me.getMemberSeq() : 0;
    String opUserId = me != null ? me.getUserId()    : "anonymous";

    // ── 지점/아이피 결정 (공인/사설 확실히 분리) ─────────────────────────────────────────────
    int    brSeq   = 0;
    String company = "";
    String pubIp  = null;   // 공인 IP
    String prvIp  = null;   // 사설 IP

    try {
      if (me != null) {
        brSeq = me.getBranchSeq();
        var b = branchSvc.findById(brSeq);
        if (b != null) {
          company = b.getCompanyName();
          pubIp   = normalizeV4(b.getPbIp());
          prvIp   = normalizeV4(b.getPIp());   // p_ip
        }
      }
    } catch (Exception ignored) {}

    // 공인 IP 비어있으면 XFF에서 추정(사설대역이면 버림)
    if (isBlank(pubIp)) {
      String xff = firstXff(req);
      if (!isBlank(xff) && !isPrivateV4(xff)) pubIp = xff;
    }
    // 사설 IP 비어있으면 NIC에서 site-local IPv4 선택 → 없으면 로컬주소
    if (isBlank(prvIp)) {
      prvIp = detectLocalPrivateIPv4();
      if (isBlank(prvIp)) prvIp = normalizeV4(req.getLocalAddr());
    }
    // IPv6 루프백 치환
    if (isLoopback(pubIp)) pubIp = "127.0.0.1";
    if (isLoopback(prvIp)) prvIp = "127.0.0.1";

    // ── SpEL 컨텍스트 ────────────────────────────────────────────────────────────────
    MethodSignature sig = (MethodSignature) jp.getSignature();
    Object[] args = jp.getArgs();
    String[] names = pd.getParameterNames(sig.getMethod());

    Integer idValue = null;
    if (names != null) {
      for (int i = 0; i < names.length && i < args.length; i++) {
        if (("memberSeq".equals(names[i]) || "id".equals(names[i])) && args[i] instanceof Integer v) { idValue = v; break; }
      }
    }

    StandardEvaluationContext ctx = new StandardEvaluationContext();
    ctx.setBeanResolver(new BeanFactoryResolver(beanFactory));
    for (int i = 0; i < args.length; i++) ctx.setVariable("p" + i, args[i]);
    if (args.length > 0 && args[0] instanceof java.util.List<?> list) ctx.setVariable("grants", list);
    if (names != null) for (int i = 0; i < names.length && i < args.length; i++) ctx.setVariable(names[i], args[i]);
    ctx.setVariable("id", idValue);
    ctx.setVariable("memberSeq", idValue);
    ctx.setVariable("userId", operatorUserId);
    ctx.setVariable("dto", args.length > 0 ? args[args.length - 1] : null);
    ctx.setVariable("return", result);
    ctx.setVariable("principal", auth != null ? auth.getPrincipal() : null);
    ctx.setVariable("recordSvc", recordSvc);
    ctx.setVariable("tmemberService", memberSvc);

    String contents = evalOrLiteral(logActivity.contents(), ctx);
    String dir      = evalOrLiteral(logActivity.dir(), ctx);
    String type     = evalOrLiteral(logActivity.type(), ctx);
    String activity = evalOrLiteral(logActivity.activity(), ctx);

    // 저장
    logSvc.createLog(TactivitylogDto.builder()
        .type        (type     != null ? type     : logActivity.type())
        .activity    (activity != null ? activity : logActivity.activity())
        .contents    (contents)
        .dir         (dir)
        .branchSeq   (brSeq)
        .companyName (company)
        .memberSeq   (opSeq)
        .userId      (operatorUserId)
        .employeeId  (0)
        .pbIp        (pubIp)     // 공인 IP
        .pvIp        (prvIp)     // 사설 IP
        .crtime      (LocalDateTime.now().format(FMT))
        .workerSeq   (opSeq)
        .workerId    (opUserId)
        .build());

    return result;
  }

  // ───────────────────────────── helpers ─────────────────────────────
  private String evalOrLiteral(String expr, StandardEvaluationContext ctx) {
    if (expr == null) return null;
    String s = expr.trim();
    if (s.isEmpty()) return null;
    try {
      if (s.contains("#{")) return parser.parseExpression(s, template).getValue(ctx, String.class);
      boolean seemsSpel = s.startsWith("'") || s.startsWith("\"") || s.startsWith("T(") || s.contains("#");
      if (seemsSpel) return parser.parseExpression(s).getValue(ctx, String.class);
      return s;
    } catch (Exception e) {
      log.warn("LogActivity evaluation failed, fallback to literal. expr={}", s, e);
      return s;
    }
  }

  private String firstXff(HttpServletRequest req) {
    String xff = req.getHeader("X-Forwarded-For");
    if (isBlank(xff)) return null;
    String ip = xff.split(",")[0].trim();
    return normalizeV4(ip);
  }

  private String detectLocalPrivateIPv4() {
    try {
      Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
      while (nics.hasMoreElements()) {
        NetworkInterface ni = nics.nextElement();
        if (!ni.isUp() || ni.isLoopback()) continue;
        Enumeration<InetAddress> addrs = ni.getInetAddresses();
        while (addrs.hasMoreElements()) {
          InetAddress a = addrs.nextElement();
          if (!(a instanceof Inet4Address)) continue;
          String ip = a.getHostAddress();
          if (isPrivateV4(ip)) return ip;
        }
      }
    } catch (SocketException ignored) {}
    return null;
  }

  private boolean isPrivateV4(String ip) {
    if (isBlank(ip)) return false;
    return ip.startsWith("10.") ||
        ip.startsWith("192.168.") ||
        ip.startsWith("172.16.") || ip.startsWith("172.17.") || ip.startsWith("172.18.") ||
        ip.startsWith("172.19.") || ip.startsWith("172.2")   || ip.startsWith("172.3") ||
        ip.startsWith("127.");
  }

  private String normalizeV4(String ip) {
    if (isBlank(ip)) return null;
    if ("0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) return "127.0.0.1";
    if (ip.startsWith("::ffff:")) return ip.substring(7);
    return ip;
  }

  private boolean isLoopback(String ip) {
    return "127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip);
  }

  private boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }
}
