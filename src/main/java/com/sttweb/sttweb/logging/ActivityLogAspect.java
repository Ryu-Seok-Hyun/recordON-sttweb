package com.sttweb.sttweb.logging;

import com.sttweb.sttweb.dto.TactivitylogDto;
import com.sttweb.sttweb.dto.TmemberDto.Info;
import com.sttweb.sttweb.dto.TmemberDto.LoginRequest;
import com.sttweb.sttweb.dto.TmemberDto.SignupRequest;
import com.sttweb.sttweb.service.TactivitylogService;
import com.sttweb.sttweb.service.TmemberService;
import com.sttweb.sttweb.service.TbranchService;
import java.lang.reflect.Method;
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

@Aspect
@Component
@RequiredArgsConstructor
public class ActivityLogAspect {
  private static final Logger log = LoggerFactory.getLogger(ActivityLogAspect.class);
  private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private final SpelExpressionParser parser = new SpelExpressionParser();
  private final ParameterNameDiscoverer pd = new DefaultParameterNameDiscoverer();
  private final TactivitylogService logService;
  private final TmemberService memberSvc;
  private final TbranchService branchSvc;

  @Around("@annotation(logActivity)")
  public Object around(ProceedingJoinPoint jp, LogActivity logActivity) throws Throwable {
    // 1) 비즈니스 로직
    Object result = jp.proceed();

    // 2) HTTP 요청이 아닐 때
    ServletRequestAttributes sa = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    if (sa == null) return result;
    HttpServletRequest req = sa.getRequest();

    // 3) 인증 정보
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    String operatorUserId = "";
    String operatorWorkerId = "anonymous";
    int    operatorSeq = 0;
    if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
      operatorUserId = auth.getName();
      Info me = memberSvc.getMyInfoByUserId(operatorUserId);
      operatorWorkerId = me.getUserId();
      operatorSeq = me.getMemberSeq();
    }

    // 로그인/회원가입 시 파라미터에서 userId 추출
    if (operatorUserId.isEmpty()) {
      for (Object arg : jp.getArgs()) {
        if (arg instanceof LoginRequest) {
          operatorUserId = ((LoginRequest)arg).getUserId();
          operatorWorkerId = operatorUserId;
          break;
        }
        if (arg instanceof SignupRequest) {
          operatorUserId = ((SignupRequest)arg).getUserId();
          operatorWorkerId = operatorUserId;
          break;
        }
      }
    }
    String userIdToLog = operatorUserId;

    // 4) 지점·IP 정보 조회
    int    branchSeq = 0;
    int    memberSeq = operatorSeq;
    String companyName = "";
    String pbIp = "", pvIp = "";
    try {
      Info userInfo = memberSvc.getMyInfoByUserId(userIdToLog);
      branchSeq = userInfo.getBranchSeq();
      var branch = branchSvc.findById(branchSeq);
      if (branch != null) {
        companyName = branch.getCompanyName();
        pbIp = branch.getPbIp();
        pvIp = branch.getPIp();
      }
    } catch (Exception ignore) {}
    if (pbIp == null || pbIp.isBlank()) {
      String xff = req.getHeader("X-Forwarded-For");
      String remote = req.getRemoteAddr();
      pbIp = (xff != null && !xff.isBlank()) ? xff.split(",")[0].trim() : remote;
      pvIp = remote;
    }

    // 5) SpEL 컨텍스트
    MethodSignature sig = (MethodSignature) jp.getSignature();
    Method method = sig.getMethod();
    Object[] args = jp.getArgs();
    StandardEvaluationContext ctx = new StandardEvaluationContext();

    // ★ 반환값 등록 (#{return.xxx} 지원)
    ctx.setVariable("return", result);
    // 파라미터 이름/순서 등록
    for (int i = 0; i < args.length; i++) ctx.setVariable("p"+i, args[i]);
    String[] names = pd.getParameterNames(method);
    if (names != null) for (int i = 0; i < names.length; i++) ctx.setVariable(names[i], args[i]);
    ctx.setVariable("principal", auth != null ? auth.getPrincipal() : null);

    // 6) contents 평가
    String expr = logActivity.contents();
    String contents = "";
    if (expr != null && expr.contains("#{")) {
      try {
        contents = parser.parseExpression(expr, new TemplateParserContext())
            .getValue(ctx, String.class);
      } catch (EvaluationException ex) {
        log.error("SpEL evaluation failed: {}", expr, ex);
        contents = expr;
      }
    } else if (expr != null) {
      contents = expr;
    }

    // dir 평가 (필요 시)
    String dirExpr = logActivity.dir();
    String dir = "";
    if (dirExpr != null && dirExpr.contains("#{")) {
      try {
        dir = parser.parseExpression(dirExpr, new TemplateParserContext())
            .getValue(ctx, String.class);
      } catch (EvaluationException ex) {
        dir = dirExpr;
      }
    } else if (dirExpr != null) {
      dir = dirExpr;
    }

    // 7) DTO 저장
    TactivitylogDto dto = TactivitylogDto.builder()
        .type(logActivity.type())
        .activity(logActivity.activity())
        .contents(contents)
        .dir(dir)
        .branchSeq(branchSeq)
        .companyName(companyName)
        .memberSeq(memberSeq)
        .userId(userIdToLog)
        .employeeId(0)
        .pbIp(pbIp)
        .pvIp(pvIp)
        .crtime(LocalDateTime.now().format(FMT))
        .workerSeq(operatorSeq)
        .workerId(operatorWorkerId)
        .build();
    logService.createLog(dto);

    return result;
  }
}