package dev.licaza.itacatl.service.impl;

import dev.licaza.itacatl.domain.Business;
import dev.licaza.itacatl.domain.Item;
import dev.licaza.itacatl.dto.CatalogItemDTO;
import dev.licaza.itacatl.dto.ItemRequestDTO;
import dev.licaza.itacatl.exception.ResourceNotFoundException;
import dev.licaza.itacatl.repo.BusinessRepository;
import dev.licaza.itacatl.repo.ItemRepository;
import dev.licaza.itacatl.service.CatalogService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CatalogDatabaseService implements CatalogService {

  private static final Logger log = LoggerFactory.getLogger(CatalogDatabaseService.class);

  private final BusinessRepository businessRepository;
  private final ItemRepository itemRepository;

  public CatalogDatabaseService(
      BusinessRepository businessRepository, ItemRepository itemRepository) {
    this.businessRepository = businessRepository;
    this.itemRepository = itemRepository;
  }

  @Override
  @Transactional(readOnly = true)
  public List<CatalogItemDTO> getItems(String slug) {
    return itemRepository.findByTenantIdOrderByNameAsc(tenantId(slug)).stream()
        .map(CatalogDatabaseService::toDTO)
        .toList();
  }

  @Override
  public CatalogItemDTO createItem(String slug, ItemRequestDTO request) {
    Item item = new Item();
    item.setTenantId(tenantId(slug));
    apply(item, request);
    item = itemRepository.save(item);
    log.info("✅ Item {} created in {}", item.getId(), slug);
    return toDTO(item);
  }

  @Override
  public CatalogItemDTO updateItem(String slug, Long itemId, ItemRequestDTO request) {
    Item item = item(slug, itemId);
    apply(item, request);
    return toDTO(item);
  }

  @Override
  public CatalogItemDTO archiveItem(String slug, Long itemId) {
    Item item = item(slug, itemId);
    item.setArchived(true);
    return toDTO(item);
  }

  @Override
  public CatalogItemDTO unarchiveItem(String slug, Long itemId) {
    Item item = item(slug, itemId);
    item.setArchived(false);
    return toDTO(item);
  }

  private Long tenantId(String slug) {
    return businessRepository
        .findBySlug(slug)
        .map(Business::getId)
        .orElseThrow(() -> ResourceNotFoundException.business(slug));
  }

  // Scoped to the business in the URL, so an owner cannot reach another business's item by
  // guessing its ID (insecure direct object reference)
  private Item item(String slug, Long itemId) {
    return itemRepository
        .findByIdAndTenantId(itemId, tenantId(slug))
        .orElseThrow(() -> ResourceNotFoundException.item(itemId));
  }

  private static void apply(Item item, ItemRequestDTO request) {
    item.setName(request.name().strip());
    item.setDescription(
        request.description() == null || request.description().isBlank()
            ? null
            : request.description().strip());
    item.setSuggestedPrice(request.suggestedPrice());
    item.setAlcoholic(request.alcoholic());
  }

  private static CatalogItemDTO toDTO(Item item) {
    return new CatalogItemDTO(
        item.getId(),
        item.getName(),
        item.getDescription(),
        item.getSuggestedPrice(),
        item.isAlcoholic(),
        item.isArchived());
  }
}
