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

  private static final Logger              log = LoggerFactory.getLogger(ActivityLogAspect.class);
  private static final DateTimeFormatter   FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private final SpelExpressionParser       parser   = new SpelExpressionParser();
  private final ParameterNameDiscoverer    pd       = new DefaultParameterNameDiscoverer();
  private final TactivitylogService        logSvc;
  private final TmemberService             memberSvc;
  private final TbranchService             branchSvc;
  private final TrecordService             recordSvc;
  private final JwtTokenProvider           jwt;
  private final BeanFactory                beanFactory;

  @Around("@annotation(logActivity)")
  public Object around(ProceedingJoinPoint jp, LogActivity logActivity) throws Throwable {

    /* 1. 비즈니스 로직 */
    Object result = jp.proceed();

    /* 2. HTTP 요청이 아닌 경우 스킵 */
    ServletRequestAttributes sa = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    if (sa == null) return result;
    HttpServletRequest req = sa.getRequest();

    /* 3. 오퍼레이터 식별 */
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    String operatorUserId = "";
    if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
      operatorUserId = auth.getName();
    }
    for (Object a : jp.getArgs()) {
      if (operatorUserId.isEmpty() && a instanceof LoginRequest lr)   operatorUserId = lr.getUserId();
      if (operatorUserId.isEmpty() && a instanceof SignupRequest sr)  operatorUserId = sr.getUserId();
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

    Info   me       = operatorUserId.isBlank() ? null : memberSvc.getMyInfoByUserId(operatorUserId);
    int    opSeq    = me != null ? me.getMemberSeq() : 0;
    String opUserId = me != null ? me.getUserId()    : "anonymous";

    /* 4. 브랜치 / IP */
    int    brSeq   = 0;
    String company = "";
    String pbIp    = "";
    String pvIp    = "";
    try {
      Info ui = memberSvc.getMyInfoByUserId(operatorUserId);
      brSeq   = ui.getBranchSeq();
      var b   = branchSvc.findById(brSeq);
      if (b != null) {
        company = b.getCompanyName();
        pbIp    = b.getPbIp();
        pvIp    = b.getPIp();
      }
    } catch (Exception ignored) { }

    if (pbIp == null || pbIp.isBlank()) {
      String nicIp = detectBranchIpFromLocalNics();
      if (nicIp != null) {
        pbIp = pvIp = nicIp;
      } else {
        String xff = req.getHeader("X-Forwarded-For");
        String rem = req.getRemoteAddr();
        pbIp = (xff != null && !xff.isBlank()) ? xff.split(",")[0].trim() : rem;
        pvIp = rem;
      }
    }

    /* 5. 메서드 파라미터 및 idValue 추출 */
    MethodSignature sig   = (MethodSignature) jp.getSignature();
    Object[]        args  = jp.getArgs();
    String[]        names = pd.getParameterNames(sig.getMethod());

    Integer idValue = null;
    if (names != null) {
      for (int i = 0; i < names.length && i < args.length; i++) {
        if (("memberSeq".equals(names[i]) || "id".equals(names[i])) && args[i] instanceof Integer v) {
          idValue = v; break;
        }
      }
    }

    /* 6. SpEL 컨텍스트 */
    StandardEvaluationContext ctx = new StandardEvaluationContext();
    ctx.setBeanResolver(new BeanFactoryResolver(beanFactory));

    for (int i = 0; i < args.length; i++) ctx.setVariable("p" + i, args[i]);  // #p0 …
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

    /* 7. contents 평가 */
    String expr = logActivity.contents();
    String contents;
    try {
      if (expr != null && expr.contains("#{")) {                 // 템플릿 식
        contents = parser
            .parseExpression(expr, new TemplateParserContext())// ← Template 모드
            .getValue(ctx, String.class);
      } else {                                                   // 순수 SpEL 식
        contents = parser.parseExpression(expr).getValue(ctx, String.class);
      }
    } catch (Exception ex) {
      log.error("LogActivity contents evaluation failed: {}", expr, ex);
      contents = expr;                                           // 실패 시 원문 저장
    }

    /* 8. dir 평가도 동일하게 */
    String dirExpr = logActivity.dir();
    String dir;
    try {
      dir = parser.parseExpression(dirExpr).getValue(ctx, String.class);
    } catch (Exception ex) {
      log.warn("LogActivity dir evaluation failed: {}", dirExpr, ex);
      dir = dirExpr;
    }

    /* 9. DB 저장 */
    logSvc.createLog(TactivitylogDto.builder()
        .type        (logActivity.type())
        .activity    (logActivity.activity())
        .contents    (contents)
        .dir         (dir)
        .branchSeq   (brSeq)
        .companyName (company)
        .memberSeq   (opSeq)
        .userId      (operatorUserId)
        .employeeId  (0)
        .pbIp        (pbIp)
        .pvIp        (pvIp)
        .crtime      (LocalDateTime.now().format(FMT))
        .workerSeq   (opSeq)
        .workerId    (opUserId)
        .build());

    return result;
  }

  /* NIC에서 브랜치 IP 추정 */
  private String detectBranchIpFromLocalNics() {
    try {
      Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
      while (nics.hasMoreElements()) {
        NetworkInterface ni = nics.nextElement();
        if (ni.isLoopback() || !ni.isUp()) continue;
        Enumeration<InetAddress> addrs = ni.getInetAddresses();
        while (addrs.hasMoreElements()) {
          InetAddress addr = addrs.nextElement();
          if (addr.isLoopbackAddress() || addr.isLinkLocalAddress()) continue;
          String ip = addr.getHostAddress();
          if (!ip.contains(".")) continue;
          if (branchSvc.findBypIp(ip).isPresent() || branchSvc.findByPbIp(ip).isPresent()) return ip;
        }
      }
    } catch (SocketException ignored) { }
    return null;
  }
}
