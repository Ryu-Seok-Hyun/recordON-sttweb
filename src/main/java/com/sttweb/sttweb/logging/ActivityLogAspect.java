package com.sttweb.sttweb.logging;

import com.sttweb.sttweb.dto.TactivitylogDto;
import com.sttweb.sttweb.dto.TmemberDto.Info;
import com.sttweb.sttweb.dto.TmemberDto.LoginRequest;
import com.sttweb.sttweb.dto.TmemberDto.SignupRequest;
import com.sttweb.sttweb.service.TactivitylogService;
import com.sttweb.sttweb.service.TmemberService;
import com.sttweb.sttweb.service.TbranchService;   // [추가]
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
import java.lang.reflect.Method;

@Aspect
@Component
@RequiredArgsConstructor
public class ActivityLogAspect {

  private static final Logger log = LoggerFactory.getLogger(ActivityLogAspect.class);

  private final SpelExpressionParser parser = new SpelExpressionParser();
  private final ParameterNameDiscoverer pd = new DefaultParameterNameDiscoverer();

  private final TactivitylogService logService;
  private final TmemberService memberSvc;
  private final TbranchService branchService;  // [추가]

  private static final DateTimeFormatter FMT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  @Around("@annotation(logActivity)")
  public Object around(ProceedingJoinPoint jp, LogActivity logActivity) throws Throwable {
    Object result = jp.proceed();

    ServletRequestAttributes sa =
        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    if (sa == null) {
      return result;
    }
    HttpServletRequest req = sa.getRequest();

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();

    String operatorUserId   = "";
    String operatorWorkerId = "anonymous";
    int    operatorSeq      = 0;
    Integer operatorBranchSeq = null;  // [추가]

    if (auth != null
        && auth.isAuthenticated()
        && !(auth instanceof AnonymousAuthenticationToken)) {
      operatorUserId = auth.getName();
      Info me = memberSvc.getMyInfoByUserId(operatorUserId);
      operatorWorkerId = me.getUserId();
      operatorSeq      = me.getMemberSeq();
      operatorBranchSeq = me.getBranchSeq(); // [추가]
    }

    String userIdToLog = operatorUserId;

    if ("record".equals(logActivity.type())) {
      Object[] args = jp.getArgs();
      if (args != null && args.length > 0 && args[0] != null) {
        userIdToLog = args[0].toString();
      }
    }

    if (operatorUserId.isEmpty()) {
      for (Object arg : jp.getArgs()) {
        if (arg instanceof LoginRequest) {
          userIdToLog = ((LoginRequest) arg).getUserId();
          break;
        }
        if (arg instanceof SignupRequest) {
          userIdToLog = ((SignupRequest) arg).getUserId();
          break;
        }
      }
      operatorWorkerId = userIdToLog;
    }

    // [중요!] branchSeq 셋팅(0/null => 불가), companyName도 추출
    int    branchSeq   = (operatorBranchSeq != null) ? operatorBranchSeq : 0;
    int    memberSeq   = operatorSeq;
    int    employeeId  = 0;
    String companyName = "";

    try {
      if (branchSeq != 0) {
        companyName = branchService.findById(branchSeq).getCompanyName();
      }
    } catch (Exception e) {
      companyName = "";
    }

    // IP 정보 - X-Forwarded-For, RemoteAddr, branchSeq 기반 pvIp 보정
    String xff = req.getHeader("X-Forwarded-For");
    String pbIp = null;
    if (xff != null && !xff.isBlank()) {
      pbIp = xff.split(",")[0].trim();
    } else {
      pbIp = req.getRemoteAddr();
    }

    // [중요] pvIp: branchSeq로 tbranch에서 p_ip 조회 (실패시 "-")
    String pvIp = "-";
    try {
      if (branchSeq != 0) {
        pvIp = branchService.findById(branchSeq).getPIp();
        if (pvIp == null || pvIp.isBlank()) pvIp = "-";
      }
    } catch (Exception e) {
      pvIp = "-";
    }

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
    ctx.setVariable("principal", auth != null ? auth.getPrincipal() : null);

    String expr = logActivity.contents();
    String contents = "";
    if (expr != null && expr.contains("#{")) {
      try {
        contents = parser
            .parseExpression(expr, new TemplateParserContext())
            .getValue(ctx, String.class);
      } catch (EvaluationException ex) {
        contents = expr;
      }
    } else if (expr != null) {
      contents = expr;
    }

    String dirExpr = logActivity.dir();
    String dir = "";
    if (dirExpr != null && dirExpr.contains("#{")) {
      try {
        dir = parser
            .parseExpression(dirExpr, new TemplateParserContext())
            .getValue(ctx, String.class);
      } catch (EvaluationException ex) {
        dir = dirExpr;
      }
    } else if (dirExpr != null) {
      dir = dirExpr;
    }

    TactivitylogDto dto = TactivitylogDto.builder()
        .type(logActivity.type())
        .activity(logActivity.activity())
        .contents(contents)
        .dir(dir)
        .branchSeq(branchSeq)
        .companyName(companyName)
        .memberSeq(memberSeq)
        .userId(userIdToLog)
        .employeeId(employeeId)
        .pbIp(pbIp)
        .pvIp(pvIp)
        .crtime(LocalDateTime.now().format(FMT))
        .workerSeq(operatorSeq)
        .workerId(operatorWorkerId)
        .build();

    log.info("[ActivityLog DTO] {}", dto);
    logService.createLog(dto);

    return result;
  }
}
