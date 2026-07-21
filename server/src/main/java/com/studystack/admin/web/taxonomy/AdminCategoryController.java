package com.studystack.admin.web.taxonomy;

import com.studystack.admin.application.AdminTaxonomyUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/categories")
public final class AdminCategoryController {

    private final AdminTaxonomyUseCase taxonomy;

    public AdminCategoryController(AdminTaxonomyUseCase taxonomy) {
        this.taxonomy = taxonomy;
    }

    @GetMapping
    public List<AdminTaxonomyResponse> list() {
        return taxonomy.listCategories().stream().map(AdminTaxonomyResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<AdminTaxonomyResponse> create(
            @Valid @RequestBody AdminTaxonomyRequest.Create request) {
        AdminTaxonomyResponse response = AdminTaxonomyResponse.from(taxonomy.createCategory(request.toCommand()));
        return ResponseEntity.created(URI.create("/api/v1/admin/categories/" + response.id())).body(response);
    }

    @PutMapping("/{id}")
    public AdminTaxonomyResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody AdminTaxonomyRequest.Update request) {
        return AdminTaxonomyResponse.from(taxonomy.updateCategory(id, request.toCommand()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @RequestParam @PositiveOrZero long version) {
        taxonomy.deleteCategory(id, version);
        return ResponseEntity.noContent().build();
    }
}
