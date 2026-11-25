package com.factoryflow.dispatch;

import com.factoryflow.dispatch.DispatchService.CreateDispatchRequest;
import com.factoryflow.dispatch.DispatchService.DispatchDto;
import com.factoryflow.users.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dispatches")
public class DispatchController {

    private final DispatchService dispatchService;

    public DispatchController(DispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','OWNER')")
    public ResponseEntity<List<DispatchDto>> list() {
        return ResponseEntity.ok(dispatchService.getAll());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DispatchDto> create(@Valid @RequestBody CreateDispatchRequest req,
                                              @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(dispatchService.createDispatch(req, user));
    }
}
