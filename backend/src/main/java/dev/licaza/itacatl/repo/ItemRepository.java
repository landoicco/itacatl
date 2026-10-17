package dev.licaza.itacatl.repo;

import dev.licaza.itacatl.domain.Item;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemRepository extends JpaRepository<Item, Long> {}
