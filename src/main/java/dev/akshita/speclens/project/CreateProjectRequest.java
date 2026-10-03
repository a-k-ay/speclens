package dev.akshita.speclens.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Limits match the column sizes in V1__create_project.sql. */
public record CreateProjectRequest(
		@NotBlank @Size(max = 120) String name,
		@Size(max = 500) String description) {
}
