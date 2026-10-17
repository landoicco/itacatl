package dev.licaza.itacatl.controller;

import dev.licaza.itacatl.dto.BusinessDetailDTO;
import dev.licaza.itacatl.dto.BusinessSummaryDTO;
import dev.licaza.itacatl.service.BusinessService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/businesses")
public class BusinessController {

  private final BusinessService businessService;

  public BusinessController(BusinessService businessService) {
    this.businessService = businessService;
  }

  @GetMapping
  public ResponseEntity<List<BusinessSummaryDTO>> getBusinesses() {
    return ResponseEntity.ok(businessService.getBusinesses());
  }

  // Unknown slugs become a 404 Problem Detail in GlobalExceptionHandler
  @GetMapping("/{slug}")
  public ResponseEntity<BusinessDetailDTO> getBusiness(@PathVariable String slug) {
    return ResponseEntity.ok(businessService.getBusiness(slug));
  }
}
