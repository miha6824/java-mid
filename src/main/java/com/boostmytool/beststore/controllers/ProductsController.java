package com.boostmytool.beststore.controllers;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.boostmytool.beststore.models.Products;
import com.boostmytool.beststore.models.ProductDto;
import com.boostmytool.beststore.models.Products.Status;
import com.boostmytool.beststore.services.ProductsRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Date;

@RestController
@RequestMapping("/api/products")
public class ProductsController {

    private final ProductsRepository repo;

    @Autowired
    public ProductsController(ProductsRepository repo) {
        this.repo = repo;
    }

    @PostMapping
    public Products createProduct(
        @Valid @RequestBody ProductDto productDto,
        @RequestParam("imageFile") MultipartFile imageFile) throws IOException {

        if (imageFile.isEmpty()) {
            throw new IllegalArgumentException("The image file is required");
        }

        String storageFileName = saveImageFile(imageFile);

        Products product = new Products();
        product.setName(productDto.getName());
        product.setPrice(productDto.getPrice());
        product.setDiscountPrice(productDto.getDiscountPrice());
        product.setCategory(productDto.getCategory());
        product.setStatus(Status.AVAILABLE);  // Default status
        product.setDescription(productDto.getDescription());
        product.setCreatedAt(new Date());
        product.setImageFileName(storageFileName);

        return repo.save(product);
    }

    @GetMapping("/{id}")
    public Products getProduct(@PathVariable Long id) {
        return repo.findById(id)
                   .orElseThrow(() -> new RuntimeException("Product not found with ID: " + id));
    }

    @PutMapping("/{id}")
    public Products updateProduct(
        @PathVariable Long id,
        @Valid @RequestBody ProductDto productDto,
        @RequestParam(value = "imageFile", required = false) MultipartFile imageFile) throws IOException {

        Products product = repo.findById(id)
            .orElseThrow(() -> new RuntimeException("Product not found with ID: " + id));

        product.setName(productDto.getName());
        product.setPrice(productDto.getPrice());
        product.setDiscountPrice(productDto.getDiscountPrice());
        product.setCategory(productDto.getCategory());
        product.setDescription(productDto.getDescription());

        if (imageFile != null && !imageFile.isEmpty()) {
            String storageFileName = saveImageFile(imageFile);
            product.setImageFileName(storageFileName);
        }

        return repo.save(product);
    }

    @DeleteMapping("/{id}")
    public void deleteProduct(@PathVariable Long id) {
        Products product = repo.findById(id)
            .orElseThrow(() -> new RuntimeException("Product not found with ID: " + id));

        // Delete the product image
        Path imagePath = Paths.get("public/Image/" + product.getImageFileName());
        try {
            if (Files.exists(imagePath)) {
                Files.delete(imagePath);
            }
        } catch (IOException ex) {
            throw new RuntimeException("Exception deleting image file: " + ex.getMessage(), ex);
        }

        // Delete the product from the database
        repo.delete(product);
    }

    @GetMapping
    public Page<Products> getProducts(
        @RequestParam(value = "name", required = false) String name,
        @RequestParam(value = "minPrice", required = false) Double minPrice,
        @RequestParam(value = "maxPrice", required = false) Double maxPrice,
        @RequestParam(value = "page", defaultValue = "0") int page,
        @RequestParam(value = "size", defaultValue = "10") int size) {

        Specification<Products> spec = Specification.where(null);

        if (name != null && !name.trim().isEmpty()) {
            spec = spec.and((root, query, criteriaBuilder) ->
                criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), "%" + name.trim().toLowerCase() + "%"));
        }

        if (minPrice != null) {
            spec = spec.and((root, query, criteriaBuilder) ->
                criteriaBuilder.greaterThanOrEqualTo(root.get("price"), minPrice));
        }

        if (maxPrice != null) {
            spec = spec.and((root, query, criteriaBuilder) ->
                criteriaBuilder.lessThanOrEqualTo(root.get("price"), maxPrice));
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        return repo.findAll(spec, pageable);
    }

    private String saveImageFile(MultipartFile image) throws IOException {
        String storageFileName = new Date().getTime() + "_" + image.getOriginalFilename();
        String uploadDir = "public/Image/";  // Ensure this path is correct

        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        try (var inputStream = image.getInputStream()) {
            Files.copy(inputStream, uploadPath.resolve(storageFileName), StandardCopyOption.REPLACE_EXISTING);
        }

        return storageFileName;
    }
}
