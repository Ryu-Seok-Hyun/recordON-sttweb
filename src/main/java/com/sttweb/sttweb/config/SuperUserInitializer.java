package com.sttweb.sttweb.config;

import com.sttweb.sttweb.entity.TmemberEntity;
import com.sttweb.sttweb.repository.TmemberRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SuperUserInitializer implements CommandLineRunner {

  private static final Logger log = LoggerFactory.getLogger(SuperUserInitializer.class);

  private final TmemberRepository memberRepo;
  private final PasswordEncoder   passwordEncoder;

  @Override
  public void run(String... args) throws Exception {
    if (!memberRepo.existsByUserId("IQ200admin")) {
      TmemberEntity admin = TmemberEntity.builder()
          .userId("IQ200admin")
          .userPass(passwordEncoder.encode("pwbplguplus1!"))
          .userLevel("3")    // 슈퍼유저 레벨
          .number("0000")    // NOT NULL
          .maskFlag(1)       // 마스킹 안 함
          .discd(0)          // 조회 이력 컬럼(default 0)
          .roleSeq(4)        // 조회+청취+다운로드 권한
          .reguserId("system")
          .build();

      memberRepo.save(admin);
      log.info("▶ Superuser IQ200admin 생성 완료");
    }
  }
}
