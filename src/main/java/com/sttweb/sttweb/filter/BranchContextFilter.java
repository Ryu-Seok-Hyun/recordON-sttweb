package com.sttweb.sttweb.filter;

import com.sttweb.sttweb.context.BranchContext;
import com.sttweb.sttweb.service.TmemberService;
import com.sttweb.sttweb.jwt.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;


public class BranchContextFilter extends OncePerRequestFilter {

  private final JwtTokenProvider jwtProvider;
  private final TmemberService memberSvc;

  public BranchContextFilter(JwtTokenProvider jwtProvider,
      TmemberService memberSvc) {
    this.jwtProvider = jwtProvider;
    this.memberSvc  = memberSvc;
  }
  @Override
  protected void doFilterInternal(HttpServletRequest req,
      HttpServletResponse res,
      FilterChain chain)
      throws ServletException, IOException {
    String auth   = req.getHeader("Authorization");
    String branch = req.getHeader("X-Branch-Id");

    if (auth != null && auth.startsWith("Bearer ") && branch != null) {
      String token = auth.substring(7).trim();
      if (!jwtProvider.validateToken(token)) {
        res.setStatus(HttpStatus.UNAUTHORIZED.value());
        return;
      }

      String userId;
      try {
        userId = jwtProvider.getUserId(token);
      } catch (Exception e) {
        res.setStatus(HttpStatus.UNAUTHORIZED.value());
        return;
      }

      Integer branchId;
      try {
        branchId = Integer.valueOf(branch);
      } catch (NumberFormatException e) {
        res.setStatus(HttpStatus.BAD_REQUEST.value());
        return;
      }

      // 기존 메서드 사용
      if (!memberSvc.existsUserInBranch(userId, branchId)) {
        res.setStatus(HttpStatus.FORBIDDEN.value());
        return;
      }

      BranchContext.set(branchId);
    }

    try {
      chain.doFilter(req, res);
    } finally {
      BranchContext.clear();
    }
  }
}
