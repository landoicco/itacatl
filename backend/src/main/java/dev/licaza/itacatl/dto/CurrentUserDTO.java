package dev.licaza.itacatl.dto;

import java.util.List;

/**
 * The signed-in user, so the owner panel knows who is there and which businesses to show.
 *
 * @param businesses every business the user works for, sorted by name
 */
public record CurrentUserDTO(String email, String name, List<MembershipDTO> businesses) {}
