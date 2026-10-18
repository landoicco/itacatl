package dev.licaza.itacatl.dto;

import dev.licaza.itacatl.domain.MembershipRole;

/** A business the signed-in user works for, and their role in it. */
public record MembershipDTO(String slug, String name, MembershipRole role) {}
