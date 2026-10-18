package dev.licaza.itacatl.service;

import dev.licaza.itacatl.dto.CurrentUserDTO;

public interface AccountService {

  /**
   * The account with its businesses, read fresh from the database so a changed membership shows up
   * without signing in again.
   *
   * @throws dev.licaza.itacatl.exception.ResourceNotFoundException if the account no longer exists
   */
  CurrentUserDTO getCurrentUser(Long userId);
}
