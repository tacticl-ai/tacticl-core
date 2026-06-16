package io.tacticl.service.profile.controller;

import io.cidadel.framework.authorization.annotation.AuthUser;
import io.cidadel.framework.authorization.annotation.RequireAuth;
import io.cidadel.framework.authorization.context.AuthenticatedUser;
import io.cidadel.service.base.controller.BaseController;
import io.tacticl.business.profile.service.ProductService;
import io.tacticl.business.profile.service.ProductService.ChannelSpec;
import io.tacticl.business.profile.service.ProductService.RepoSpec;
import io.tacticl.data.profile.entity.Product;
import io.tacticl.service.profile.dto.ChannelSpecDto;
import io.tacticl.service.profile.dto.ProductDto;
import io.tacticl.service.profile.dto.RegisterProductDto;
import io.tacticl.service.profile.dto.RepoSpecDto;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Product onboarding + management: register/list/get/delete user products. */
@RestController
@RequestMapping("/v1/products")
public class ProductController extends BaseController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @Override
    protected String getModuleName() {
        return "products";
    }

    @PostMapping
    @RequireAuth
    public ResponseEntity<ProductDto> register(@AuthUser AuthenticatedUser user,
                                               @RequestBody RegisterProductDto request) {
        List<RepoSpec> repos = new ArrayList<>();
        if (request.repos() != null) {
            for (RepoSpecDto r : request.repos()) {
                repos.add(new RepoSpec(r.url(), r.create(), r.owner(), r.repoName(), r.isPrivate()));
            }
        }
        List<ChannelSpec> channels = new ArrayList<>();
        if (request.channels() != null) {
            for (ChannelSpecDto c : request.channels()) {
                channels.add(new ChannelSpec(c.channelType(), c.externalKey(), c.label()));
            }
        }
        Product product = productService.registerProduct(user.getUserId(), request.name(), repos, channels);
        return ResponseEntity.status(HttpStatus.CREATED).body(ProductDto.from(product));
    }

    @GetMapping
    @RequireAuth
    public ResponseEntity<List<ProductDto>> list(@AuthUser AuthenticatedUser user) {
        List<ProductDto> products = productService.listProducts(user.getUserId()).stream()
                .map(ProductDto::from)
                .toList();
        return ResponseEntity.ok(products);
    }

    @GetMapping("/{id}")
    @RequireAuth
    public ResponseEntity<ProductDto> get(@AuthUser AuthenticatedUser user, @PathVariable String id) {
        return productService.getProduct(user.getUserId(), id)
                .map(ProductDto::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @RequireAuth
    public ResponseEntity<Void> delete(@AuthUser AuthenticatedUser user, @PathVariable String id) {
        boolean deleted = productService.deleteProduct(user.getUserId(), id);
        return deleted ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }
}
