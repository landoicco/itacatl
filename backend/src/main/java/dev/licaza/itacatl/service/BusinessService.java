package dev.licaza.itacatl.service;

import dev.licaza.itacatl.dto.BusinessDetailDTO;
import dev.licaza.itacatl.dto.BusinessSummaryDTO;
import java.util.List;

/** Public, read-only view of the businesses on the marketplace. */
public interface BusinessService {

  /** Every business, sorted by name, with whether it is open right now. */
  List<BusinessSummaryDTO> getBusinesses();

  /**
   * A business's profile, weekly hours and the published menus that have not ended yet.
   *
   * @throws dev.licaza.itacatl.exception.ResourceNotFoundException if no business has this slug
   */
  BusinessDetailDTO getBusiness(String slug);
}
