# Contributing to AI Scheduler Backend

Thank you for your interest in contributing to the AI Scheduler Backend! This document provides guidelines and instructions for contributing.

## Code of Conduct

- Be respectful and inclusive
- Provide constructive feedback
- Help keep discussions focused and productive

## Getting Started

1. **Fork the repository** and clone your fork locally
2. **Create a feature branch**: `git checkout -b feature/your-feature-name`
3. **Set up the development environment**:
   ```bash
   ./mvnw clean install
   cp .env.example .env
   # Add your local configuration to .env
   ```
4. **Ensure tests pass**: `./mvnw test`

## Making Changes

### Code Style

- Follow [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
- Use meaningful variable and method names
- Keep methods focused and concise
- Add comments for complex logic

### Testing

- Write unit tests for new features
- Ensure all tests pass: `./mvnw test`
- Aim for >80% code coverage
- Test edge cases and error scenarios

### Commits

- Write clear, descriptive commit messages
- Use present tense: "Add feature" not "Added feature"
- Reference issues where applicable: "Fix #123"
- Keep commits logically organized (not too large, not too small)

## Submitting Changes

1. **Push to your fork**:
   ```bash
   git push origin feature/your-feature-name
   ```

2. **Create a Pull Request** on GitHub with:
   - Clear title describing the change
   - Description of what changed and why
   - Reference to related issues
   - Screenshot/demo if UI-related

3. **Code Review**:
   - Respond to reviewer feedback promptly
   - Keep the PR focused (don't mix unrelated changes)
   - Update based on feedback

4. **Merge**:
   - Ensure all checks pass
   - Squash commits if requested
   - Delete branch after merge

## Project Structure

```
src/main/java/com/ai/scheduler/
├── controller/          # REST endpoints
├── service/            # Business logic
├── repository/         # Data access
├── entity/            # JPA entities
├── security/          # Auth & JWT
├── exception/         # Error handling
├── config/            # Spring configuration
└── util/              # Helper utilities
```

## Common Development Tasks

### Run the app locally
```bash
./mvnw spring-boot:run
# Runs on http://localhost:8085
```

### Run tests with coverage
```bash
./mvnw verify
# Coverage report: target/site/jacoco/index.html
```

### Build Docker image
```bash
docker build -t ai-scheduler-backend .
docker run -p 8085:8080 ai-scheduler-backend
```

### Access API documentation
- Swagger UI: http://localhost:8085/swagger-ui.html
- OpenAPI JSON: http://localhost:8085/v3/api-docs

## Reporting Issues

Please use GitHub Issues to report bugs or request features:

- **Bug Report**: Describe the issue, steps to reproduce, and expected behavior
- **Feature Request**: Explain the use case and proposed solution
- **Include relevant details**: Java version, OS, error messages, logs

## Questions?

- Check existing issues and PRs first
- Open a discussion if unsure
- Ask for clarification in PR comments

---

Happy contributing! 🎉
