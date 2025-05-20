package com.sttweb.sttweb.logging;

import com.sttweb.sttweb.dto.TactivitylogDto;
import com.sttweb.sttweb.dto.TmemberDto.Info;
import com.sttweb.sttweb.dto.TmemberDto.LoginRequest;
import com.sttweb.sttweb.dto.TmemberDto.SignupRequest;
import com.sttweb.sttweb.service.TactivitylogService;
import com.sttweb.sttweb.service.TmemberService;
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

  // SpEL 평가기 & 파라미터 이름 추출기
  private final SpelExpressionParser parser = new SpelExpressionParser();
  private final ParameterNameDiscoverer pd = new DefaultParameterNameDiscoverer();

  private final TactivitylogService logService;
  private final TmemberService memberSvc;

  private static final DateTimeFormatter FMT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  @Around("@annotation(logActivity)")
  public Object around(ProceedingJoinPoint jp, LogActivity logActivity) throws Throwable {
    // 1) 실제 비즈니스 로직 수행
    Object result = jp.proceed();

    // 2) HTTP 요청이 아닐 경우 로깅 생략
    ServletRequestAttributes sa =
        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    if (sa == null) {
      return result;
    }
    HttpServletRequest req = sa.getRequest();

    // 3) 현재 인증 정보
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();

    // operator (로그 남기는 주체) 정보 추출
    String operatorUserId   = "";        // JWT 인증 시 auth.getName()
    String operatorWorkerId = "anonymous";
    int    operatorSeq      = 0;

    if (auth != null
        && auth.isAuthenticated()
        && !(auth instanceof AnonymousAuthenticationToken)) {
      // JWT 인증된 사용자
      operatorUserId = auth.getName();
      Info me = memberSvc.getMyInfoByUserId(operatorUserId);
      operatorWorkerId = me.getUserId();    // 실제 userId
      operatorSeq      = me.getMemberSeq(); // memberSeq
    }

    // 4) 기본 user_id 로 기록할 값 결정
    String userIdToLog = operatorUserId;

    // - record 타입이면 첫 파라미터(내선번호 등) 를 user_id 로 기록
    if ("record".equals(logActivity.type())) {
      Object[] args = jp.getArgs();
      if (args != null && args.length > 0 && args[0] != null) {
        userIdToLog = args[0].toString();
      }
    }

    // - 로그인/회원가입 호출 시, 로그아웃 전 세션 정보가 없으므로
    //   파라미터(LoginRequest, SignupRequest) 에서 userId 를 꺼내도록 처리
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

    // 5) 나머지 정보 기본값
    int    branchSeq   = 0;
    int    memberSeq   = operatorSeq;
    int    employeeId  = 0;
    String companyName = "";

    // 6) 클라이언트 IP
    String pbIp = req.getRemoteAddr();
    String pvIp = req.getHeader("X-Forwarded-For");

    // 7) SpEL 평가용 컨텍스트 준비
    MethodSignature sig = (MethodSignature) jp.getSignature();
    Method method = sig.getMethod();
    Object[] args = jp.getArgs();
    StandardEvaluationContext ctx = new StandardEvaluationContext();
    // args → p0, p1, …
    for (int i = 0; i < args.length; i++) {
      ctx.setVariable("p" + i, args[i]);
    }
    // 파라미터 이름 → 값
    String[] names = pd.getParameterNames(method);
    if (names != null) {
      for (int i = 0; i < names.length; i++) {
        ctx.setVariable(names[i], args[i]);
      }
    }
    // principal 변수로도 사용 가능
    ctx.setVariable("principal", auth != null ? auth.getPrincipal() : null);

    // 8) contents 평가
    //    문자열 어디에든 #{…} 가 있으면 TemplateParserContext 로 평가
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

    // 9) dir 평가
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

    // 10) DTO 생성 및 저장
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
