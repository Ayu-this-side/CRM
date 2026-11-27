package com.crm.controller;

import com.crm.dto.UserDto;
import com.crm.entity.User;
import com.crm.enums.Role;
import com.crm.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Admin-only controller for managing user accounts.
 *
 * All routes under /admin/users are protected at the security layer
 * (see SecurityConfig) and also annotated @PreAuthorize for clarity.
 */
@Controller
@RequestMapping("/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserService userService;

    // -------------------------------------------------
    // List
    // -------------------------------------------------

    @GetMapping
    public String list(Model model) {
        model.addAttribute("users", userService.findAll());
        model.addAttribute("activeMenu", "users");
        return "admin/users/list";
    }

    // -------------------------------------------------
    // Create
    // -------------------------------------------------

    @GetMapping("/new")
    public String newUserForm(Model model) {
        model.addAttribute("userDto", new UserDto());
        model.addAttribute("roles", Role.values());
        model.addAttribute("editMode", false);
        model.addAttribute("activeMenu", "users");
        return "admin/users/form";
    }

    @PostMapping
    public String createUser(@Valid @ModelAttribute("userDto") UserDto dto,
                             BindingResult bindingResult,
                             Model model,
                             RedirectAttributes redirect) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("roles", Role.values());
            model.addAttribute("editMode", false);
            model.addAttribute("activeMenu", "users");
            return "admin/users/form";
        }
        try {
            userService.createUser(dto);
            redirect.addFlashAttribute("successMessage", "User '" + dto.getName() + "' created successfully.");
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("roles", Role.values());
            model.addAttribute("editMode", false);
            model.addAttribute("activeMenu", "users");
            return "admin/users/form";
        }
        return "redirect:/admin/users";
    }

    // -------------------------------------------------
    // Edit / Update
    // -------------------------------------------------

    @GetMapping("/{id}/edit")
    public String editUserForm(@PathVariable Long id, Model model) {
        User user = userService.findById(id);

        UserDto dto = new UserDto();
        dto.setName(user.getName());
        dto.setEmail(user.getEmail());
        dto.setRole(user.getRole());
        // password intentionally left blank — user can optionally change it

        model.addAttribute("userDto", dto);
        model.addAttribute("userId", id);
        model.addAttribute("roles", Role.values());
        model.addAttribute("editMode", true);
        model.addAttribute("activeMenu", "users");
        return "admin/users/form";
    }

    @PostMapping("/{id}")
    public String updateUser(@PathVariable Long id,
                             @Valid @ModelAttribute("userDto") UserDto dto,
                             BindingResult bindingResult,
                             @AuthenticationPrincipal User currentUser,
                             Model model,
                             RedirectAttributes redirect) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("userId", id);
            model.addAttribute("roles", Role.values());
            model.addAttribute("editMode", true);
            model.addAttribute("activeMenu", "users");
            return "admin/users/form";
        }
        try {
            userService.updateUser(id, dto, currentUser);
            redirect.addFlashAttribute("successMessage", "User '" + dto.getName() + "' updated successfully.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("userId", id);
            model.addAttribute("roles", Role.values());
            model.addAttribute("editMode", true);
            model.addAttribute("activeMenu", "users");
            return "admin/users/form";
        }
        return "redirect:/admin/users";
    }

    // -------------------------------------------------
    // Activate / Deactivate
    // -------------------------------------------------

    @PostMapping("/{id}/deactivate")
    public String deactivate(@PathVariable Long id,
                             @AuthenticationPrincipal User currentUser,
                             RedirectAttributes redirect) {
        try {
            userService.deactivateUser(id, currentUser);
            redirect.addFlashAttribute("successMessage", "User deactivated. Their active sessions were revoked.");
        } catch (IllegalStateException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/activate")
    public String activate(@PathVariable Long id, RedirectAttributes redirect) {
        userService.activateUser(id);
        redirect.addFlashAttribute("successMessage", "User reactivated successfully.");
        return "redirect:/admin/users";
    }
}
