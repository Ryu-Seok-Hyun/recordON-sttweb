package com.sttweb.sttweb.logging;

import com.sttweb.sttweb.dto.TactivitylogDto;
import com.sttweb.sttweb.dto.TmemberDto.Info;
import com.sttweb.sttweb.dto.TmemberDto.LoginRequest;
import com.sttweb.sttweb.dto.TmemberDto.SignupRequest;
import com.sttweb.sttweb.service.TactivitylogService;
import com.sttweb.sttweb.service.TmemberService;
import com.sttweb.sttweb.service.TbranchService;
import com.sttweb.sttweb.service.TrecordService;
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
  private final BeanFactory beanFactory;
  private final TrecordService recordSvc;

  @Around("@annotation(logActivity)")
  public Object around(ProceedingJoinPoint jp, LogActivity logActivity) throws Throwable {
    // 1) 원본 메소드 실행
    Object result = jp.proceed();

    // 2) HTTP 요청 아니면 종료
    ServletRequestAttributes sa = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    if (sa == null) return result;
    HttpServletRequest req = sa.getRequest();

    // 3) 인증 정보 수집
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    String operatorUserId = "";
    String operatorWorkerId = "anonymous";
    int operatorSeq = 0;
    if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
      operatorUserId = auth.getName();
      Info me = memberSvc.getMyInfoByUserId(operatorUserId);
      operatorWorkerId = me.getUserId();
      operatorSeq = me.getMemberSeq();
    }
    // 로그인/회원가입 시 userId 파라미터에서 추출
    if (operatorUserId.isEmpty()) {
      for (Object arg : jp.getArgs()) {
        if (arg instanceof LoginRequest) {
          operatorUserId = ((LoginRequest) arg).getUserId();
          operatorWorkerId = operatorUserId;
          break;
        }
        if (arg instanceof SignupRequest) {
          operatorUserId = ((SignupRequest) arg).getUserId();
          operatorWorkerId = operatorUserId;
          break;
        }
      }
    }
    String userIdToLog = operatorUserId;

    // 4) 지점·IP 정보 조회
    int branchSeq = 0;
    int memberSeq = operatorSeq;
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
    } catch (Exception ignored) {}
    if (pbIp == null || pbIp.isBlank()) {
      String xff = req.getHeader("X-Forwarded-For");
      String remote = req.getRemoteAddr();
      pbIp = (xff != null && !xff.isBlank()) ? xff.split(",")[0].trim() : remote;
      pvIp = remote;
    }

    // 5) 'id' 파라미터 추출
    MethodSignature sig = (MethodSignature) jp.getSignature();
    String[] paramNames = sig.getParameterNames();
    Object[] args = jp.getArgs();
    Integer idValue = null;
    for (int i = 0; i < paramNames.length; i++) {
      if ("id".equals(paramNames[i]) && args[i] instanceof Integer) {
        idValue = (Integer) args[i];
        break;
      }
    }

    // 6) SpEL 컨텍스트 설정
    StandardEvaluationContext ctx = new StandardEvaluationContext();
    // 파라미터·리턴·principal 등록
    ctx.setVariable("id", idValue);
    ctx.setVariable("return", result);
    for (int i = 0; i < args.length; i++) {
      ctx.setVariable("p" + i, args[i]);
    }
    String[] names = pd.getParameterNames(sig.getMethod());
    if (names != null) {
      for (int i = 0; i < names.length; i++) {
        ctx.setVariable(names[i], args[i]);
      }
    }
    ctx.setVariable("principal", auth != null ? auth.getPrincipal() : null);
    // Bean 참조 허용
    ctx.setVariable("recordSvc", recordSvc);

    // 7) contents 평가
    String expr = logActivity.contents();
    String contents;
    if (expr != null && expr.contains("#{")) {
      try {
        contents = parser
            .parseExpression(expr, new TemplateParserContext())
            .getValue(ctx, String.class);
      } catch (Exception ex) {
        log.error("SpEL evaluation failed: {}", expr, ex);
        contents = expr;
      }
    } else {
      contents = expr != null ? expr : "";
    }

    // 8) dir 평가 (필요 시)
    String dirExpr = logActivity.dir();
    String dir = "";
    if (dirExpr != null && dirExpr.contains("#{")) {
      try {
        dir = parser.parseExpression(dirExpr, new TemplateParserContext()).getValue(ctx, String.class);
      } catch (Exception ex) {
        dir = dirExpr;
      }
    } else if (dirExpr != null) {
      dir = dirExpr;
    }

    // 9) DTO 생성 및 저장
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
