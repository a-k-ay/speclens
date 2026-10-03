package dev.akshita.speclens.project;

public class ProjectNotFoundException extends RuntimeException {

	public ProjectNotFoundException(long id) {
		super("Project " + id + " not found");
	}

}
