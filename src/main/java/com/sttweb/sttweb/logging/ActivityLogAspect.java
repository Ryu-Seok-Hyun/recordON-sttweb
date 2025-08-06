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
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;

@Aspect
@Component
@RequiredArgsConstructor
public class ActivityLogAspect {
  private static final Logger log = LoggerFactory.getLogger(ActivityLogAspect.class);
  private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private final SpelExpressionParser    parser         = new SpelExpressionParser();
  private final ParameterNameDiscoverer pd             = new DefaultParameterNameDiscoverer();
  private final TactivitylogService     logService;
  private final TmemberService          memberSvc;
  private final TbranchService          branchSvc;
  private final TrecordService          recordSvc;
  private final JwtTokenProvider        jwtTokenProvider;
  private final BeanFactory             beanFactory;

  @Around("@annotation(logActivity)")
  public Object around(ProceedingJoinPoint jp, LogActivity logActivity) throws Throwable {
    // 1) 실제 비즈니스 로직 수행
    Object result = jp.proceed();

    // 2) HTTP 요청이 아니면 스킵
    ServletRequestAttributes sa = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    if (sa == null) return result;
    HttpServletRequest req = sa.getRequest();

    // 3) operatorUserId 수집
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    String operatorUserId   = "";
    String operatorWorkerId = "anonymous";
    int    operatorSeq      = 0;

    // 3-1) SecurityContext
    if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
      operatorUserId = auth.getName();
    }
    // 3-2) LoginRequest/SignupRequest 파라미터
    if (operatorUserId.isEmpty()) {
      for (Object arg : jp.getArgs()) {
        if (arg instanceof LoginRequest) {
          operatorUserId = ((LoginRequest) arg).getUserId(); break;
        }
        if (arg instanceof SignupRequest) {
          operatorUserId = ((SignupRequest) arg).getUserId(); break;
        }
      }
    }
    // 3-3) ReAuth 토큰
    if (operatorUserId.isEmpty()) {
      String reAuth = req.getHeader("X-ReAuth-Token");
      if (reAuth!=null && jwtTokenProvider.validateReAuthToken(reAuth)) {
        operatorUserId = jwtTokenProvider.getUserId(reAuth);
      }
    }
    // 3-4) Authorization 헤더
    if (operatorUserId.isEmpty()) {
      String authH = req.getHeader(HttpHeaders.AUTHORIZATION);
      if (authH!=null && authH.startsWith("Bearer ")) {
        String tok = authH.substring(7).trim();
        if (jwtTokenProvider.validateToken(tok)) {
          operatorUserId = jwtTokenProvider.getUserId(tok);
        }
      }
    }
    // 3-5) 최종 세팅
    if (!operatorUserId.isEmpty()) {
      Info meInfo = memberSvc.getMyInfoByUserId(operatorUserId);
      operatorWorkerId = meInfo.getUserId();
      operatorSeq      = meInfo.getMemberSeq();
    }
    String userIdToLog = operatorUserId;

    // 4) branch/IP 조회
    int    branchSeq   = 0;
    String companyName = "";
    String pbIp        = "";
    String pvIp        = "";
    try {
      Info ui = memberSvc.getMyInfoByUserId(userIdToLog);
      branchSeq = ui.getBranchSeq();
      var b = branchSvc.findById(branchSeq);
      if (b != null) {
        companyName = b.getCompanyName();
        pbIp        = b.getPbIp();
        pvIp        = b.getPIp();
      }
    } catch (Exception ignored) {}

    // 4-1) 테이블에 IP 없으면 로컬 NIC 우선
    if (pbIp == null || pbIp.isBlank()) {
      String nicIp = detectBranchIpFromLocalNics();
      if (nicIp != null) {
        pbIp = nicIp;
        pvIp = nicIp;
      } else {
        // 4-2) X-Forwarded-For / 원격 주소
        String xff    = req.getHeader("X-Forwarded-For");
        String remote = req.getRemoteAddr();
        pbIp = (xff!=null && !xff.isBlank()) ? xff.split(",")[0].trim() : remote;
        pvIp = remote;
      }
    }

    // 5) PathVariable 'memberSeq' 또는 'id' 추출
    MethodSignature sig        = (MethodSignature) jp.getSignature();
    String[]        paramNames = sig.getParameterNames();
    Object[]        args       = jp.getArgs();
    Integer         idValue    = null;
    for (int i = 0; i < paramNames.length; i++) {
      if ("memberSeq".equals(paramNames[i]) && args[i] instanceof Integer) {
        idValue = (Integer) args[i];
        break;
      }
      if ("id".equals(paramNames[i]) && args[i] instanceof Integer) {
        idValue = (Integer) args[i];
        break;
      }
    }

    // 6) SpEL 컨텍스트 설정
    StandardEvaluationContext ctx = new StandardEvaluationContext();
    ctx.setBeanResolver(new BeanFactoryResolver(beanFactory));
    // positional args
    for (int i = 0; i < args.length; i++) {
      ctx.setVariable("p" + i, args[i]);
    }
    // named parameters
    String[] names = pd.getParameterNames(sig.getMethod());
    if (names!=null) {
      for (int i=0; i<names.length; i++) {
        ctx.setVariable(names[i], args[i]);
      }
    }
    // 공통 변수
    ctx.setVariable("id",             idValue);
    ctx.setVariable("memberSeq",      idValue);
    ctx.setVariable("userId",         userIdToLog);
    ctx.setVariable("dto",            args.length>0 ? args[args.length-1] : null);
    ctx.setVariable("return",         result);
    ctx.setVariable("principal",      auth!=null?auth.getPrincipal():null);
    ctx.setVariable("recordSvc",      recordSvc);
    ctx.setVariable("tmemberService", memberSvc);

    // 7) contents 평가
    String expr     = logActivity.contents();
    String contents;
    try {
      if (expr!=null && expr.contains("#{")) {
        contents = parser
            .parseExpression(expr, new TemplateParserContext())
            .getValue(ctx, String.class);
      } else {
        contents = expr!=null ? expr : "";
      }
    } catch(Exception ex) {
      log.error("LogActivity contents evaluation failed: {}", expr, ex);
      contents = expr!=null ? expr : "";
    }

    // 8) dir 평가
    String dir     = "";
    String dirExpr = logActivity.dir();
    if (dirExpr!=null) {
      try {
        if (dirExpr.contains("#{")) {
          dir = parser
              .parseExpression(dirExpr, new TemplateParserContext())
              .getValue(ctx, String.class);
        } else {
          dir = dirExpr;
        }
      } catch(Exception ex) {
        log.warn("LogActivity dir evaluation failed: {}", dirExpr, ex);
        dir = dirExpr;
      }
    }

    // 9) DB 저장
    TactivitylogDto dto = TactivitylogDto.builder()
        .type       (logActivity.type())
        .activity   (logActivity.activity())
        .contents   (contents)
        .dir        (dir)
        .branchSeq  (branchSeq)
        .companyName(companyName)
        .memberSeq  (operatorSeq)
        .userId     (userIdToLog)
        .employeeId (0)
        .pbIp       (pbIp)
        .pvIp       (pvIp)
        .crtime     (LocalDateTime.now().format(FMT))
        .workerSeq  (operatorSeq)
        .workerId   (operatorWorkerId)
        .build();
    logService.createLog(dto);

    return result;
  }

  /**
   * 로컬 NIC 중 브랜치 IP로 사용할 수 있는 주소(예: 192.168.56.1)를 반환합니다.
   */
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
          // 브랜치 서비스에 매핑되는 IP라면 즉시 리턴
          if (branchSvc.findBypIp(ip).isPresent() || branchSvc.findByPbIp(ip).isPresent()) {
            return ip;
          }
        }
      }
    } catch (SocketException ignored) {}
    return null;
  }
}
