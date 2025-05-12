package com.sttweb.sttweb.logging;

import com.sttweb.sttweb.dto.TactivitylogDto;
import com.sttweb.sttweb.service.TactivitylogService;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.lang.reflect.Method;

@Aspect
@Component
@RequiredArgsConstructor
public class ActivityLogAspect {

  private static final Logger log = LoggerFactory.getLogger(ActivityLogAspect.class);
  private final SpelExpressionParser parser = new SpelExpressionParser();
  private final ParameterNameDiscoverer pd = new DefaultParameterNameDiscoverer();
  private final TactivitylogService logService;

  @Around("@annotation(logActivity)")
  public Object around(ProceedingJoinPoint jp, LogActivity logActivity) throws Throwable {
    // 1) 먼저 비즈니스 로직 실행 -> 세션 정보 등이 설정됨
    Object result = jp.proceed();

    // 2) HTTP/세션 정보 조회
    ServletRequestAttributes sa = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    HttpServletRequest req = sa.getRequest();
    HttpSession session = req.getSession(false);

    Integer branchSeq   = session != null ? (Integer) session.getAttribute("branchSeq") : null;
    String  companyName = session != null ? (String)  session.getAttribute("companyName") : null;
    Integer memberSeq   = session != null ? (Integer) session.getAttribute("memberSeq") : null;
    String  userId      = session != null ? (String)  session.getAttribute("userId") : null;
    Integer employeeId  = session != null ? (Integer) session.getAttribute("employeeId") : null;
    Integer workerSeq   = session != null ? (Integer) session.getAttribute("workerSeq") : 0;
    String  workerId    = session != null ? (String)  session.getAttribute("workerId") : "unknown";

    String pbIp = req.getRemoteAddr();
    String pvIp = req.getHeader("X-Forwarded-For");

    // 3) SpEL 컨텍스트 구성 및 평가
    MethodSignature sig = (MethodSignature) jp.getSignature();
    Method method = sig.getMethod();
    Object[] args = jp.getArgs();
    StandardEvaluationContext ctx = new StandardEvaluationContext();
    for (int i = 0; i < args.length; i++) {
      ctx.setVariable("p" + i, args[i]);
    }
    String[] names = pd.getParameterNames(method);
    if (names != null) {
      for (int i = 0; i < names.length; i++) {
        ctx.setVariable(names[i], args[i]);
      }
    }
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    ctx.setVariable("principal", auth != null ? auth.getPrincipal() : null);

    String contents = "";
    String expr = logActivity.contents();
    if (expr != null && !expr.isBlank()) {
      try { contents = parser.parseExpression(expr).getValue(ctx, String.class); }
      catch (EvaluationException ignored) {}
    }
    String dir = "";
    String dirExpr = logActivity.dir();
    if (dirExpr != null && !dirExpr.isBlank()) {
      try { dir = parser.parseExpression(dirExpr).getValue(ctx, String.class); }
      catch (EvaluationException ignored) {}
    }

    // 4) DTO 생성 및 저장
    TactivitylogDto dto = TactivitylogDto.builder()
        .type(logActivity.type())
        .activity(logActivity.activity())
        .contents(contents)
        .dir(dir)
        .branchSeq(branchSeq)
        .companyName(companyName)
        .memberSeq(memberSeq)
        .userId(userId)
        .employeeId(employeeId)
        .pbIp("")
        .pvIp(pvIp)
        .crtime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
        .workerSeq(workerSeq)
        .workerId(workerId)
        .build();

    log.info("[ActivityLog DTO] {}", dto);
    logService.createLog(dto);

    // 5) 원래 결과 반환
    return result;
  }
}