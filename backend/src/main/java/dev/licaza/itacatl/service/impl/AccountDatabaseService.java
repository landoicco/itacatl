package dev.licaza.itacatl.service.impl;

import dev.licaza.itacatl.domain.AppUser;
import dev.licaza.itacatl.dto.CurrentUserDTO;
import dev.licaza.itacatl.dto.MembershipDTO;
import dev.licaza.itacatl.exception.ResourceNotFoundException;
import dev.licaza.itacatl.repo.AppUserRepository;
import dev.licaza.itacatl.repo.MembershipRepository;
import dev.licaza.itacatl.service.AccountService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountDatabaseService implements AccountService {

  private final AppUserRepository userRepository;
  private final MembershipRepository membershipRepository;

  public AccountDatabaseService(
      AppUserRepository userRepository, MembershipRepository membershipRepository) {
    this.userRepository = userRepository;
    this.membershipRepository = membershipRepository;
  }

  @Override
  @Transactional(readOnly = true)
  public CurrentUserDTO getCurrentUser(Long userId) {
    AppUser user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> ResourceNotFoundException.account(userId));

    List<MembershipDTO> businesses =
        membershipRepository.findWithBusinessByUserId(userId).stream()
            .map(
                m ->
                    new MembershipDTO(
                        m.getBusiness().getSlug(), m.getBusiness().getName(), m.getRole()))
            .toList();

    return new CurrentUserDTO(user.getEmail(), user.getName(), businesses);
  }
}
