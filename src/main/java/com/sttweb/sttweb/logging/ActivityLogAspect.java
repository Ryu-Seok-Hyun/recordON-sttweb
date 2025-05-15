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
  private final SpelExpressionParser parser = new SpelExpressionParser();
  private final ParameterNameDiscoverer pd = new DefaultParameterNameDiscoverer();
  private final TactivitylogService logService;
  private final TmemberService memberSvc;
  private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  @Around("@annotation(logActivity)")
  public Object around(ProceedingJoinPoint jp, LogActivity logActivity) throws Throwable {
    // 1) 비즈니스 로직 실행
    Object result = jp.proceed();

    // 2) HTTP 요청 정보
    ServletRequestAttributes sa =
        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    if (sa == null) {
      return result;
    }
    HttpServletRequest req = sa.getRequest();

    // 3) SecurityContext에서 인증 정보 꺼내기
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();

    // operator = 실제 로그인한 사람 정보
    String operatorUserId   = "";       // auth.getName()
    String operatorWorkerId = "anonymous";
    int    operatorSeq      = 0;

    if (auth != null
        && auth.isAuthenticated()
        && !(auth instanceof AnonymousAuthenticationToken)) {
      // JWT 인증된 경우
      operatorUserId = auth.getName();
      Info me = memberSvc.getMyInfoByUserId(operatorUserId);
      operatorWorkerId = me.getUserId();
      operatorSeq      = me.getMemberSeq();
    }

    // 4) user_id 칼럼에 무엇을 기록할지 결정
    String userIdToLog = operatorUserId;

    // 녹취 다운로드 같은 record 타입은 첫 번째 파라미터(내선번호)를 user_id 로 기록
    if ("record".equals(logActivity.type())) {
      Object[] args = jp.getArgs();
      if (args != null && args.length > 0 && args[0] != null) {
        userIdToLog = args[0].toString();
      }
    }

    // 로그인/회원가입 시에도 operatorUserId 대신 인자에서 userId 추출
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

    // 5) 기타 필드 초기화
    int    branchSeq  = 0;
    int    memberSeq  = operatorSeq;
    int    employeeId = 0;
    String companyName= "";

    // 6) 클라이언트 IP
    String pbIp = req.getRemoteAddr();
    String pvIp = req.getHeader("X-Forwarded-For");

    // 7) SpEL 컨텍스트 구성 (contents, dir 처리용)
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

    // contents 처리
    String expr = logActivity.contents();
    String contents = "";
    if (expr != null && expr.trim().startsWith("#{")) {
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

    // dir 처리
    String dirExpr = logActivity.dir();
    String dir = "";
    if (dirExpr != null && dirExpr.trim().startsWith("#{")) {
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

    // 8) 로그 DTO 생성
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

    // 9) 저장
    log.info("[ActivityLog DTO] {}", dto);
    logService.createLog(dto);

    return result;
  }
}
