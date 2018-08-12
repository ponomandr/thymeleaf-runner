package com.github.ponomandr.thymeleafrunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.nio.file.Files.exists;
import static java.nio.file.Files.isRegularFile;

@Controller
public class TemplateController {

    private final ObjectMapper objectMapper;

    @Value("${app.templates}")
    private Path templateDir;

    @Value("${app.static}")
    private Path staticDir;

    @Value("${app.data}")
    private File dataFile;

    public TemplateController(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @GetMapping("/**")
    public ModelAndView handleRequest(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String requestedPath = stripStartSlash(request.getRequestURI());

        JsonNode rootNode = objectMapper.readTree(dataFile);

        for (JsonNode node : rootNode) {
            String path = stripStartSlash(node.get("path").asText());
            if (path.equals(requestedPath)) {
                String template = node.get("template").asText();
                Path templateFile = templateDir.resolve(stripStartSlash(template));
                if (exists(templateFile)) {
                    Map model = objectMapper.convertValue(node.get("data"), Map.class);
                    return new ModelAndView("file:" + templateFile, model);
                } else {
                    return new ModelAndView("classpath:/templates/template-file-not-found", HttpStatus.INTERNAL_SERVER_ERROR)
                            .addObject("file", templateFile.toAbsolutePath().toString());
                }
            }
        }

        Path staticFile = staticDir.resolve(requestedPath);
        if (isRegularFile(staticFile)) {
            Files.copy(staticFile, response.getOutputStream());
            return null;
        }

        if (hasExtension(staticFile)) {
            return new ModelAndView("classpath:/templates/static-file-not-found", HttpStatus.NOT_FOUND)
                    .addObject("file", staticFile.toAbsolutePath().toString());
        } else {
            return new ModelAndView("classpath:/templates/path-not-found", HttpStatus.NOT_FOUND)
                    .addObject("path", requestedPath)
                    .addObject("paths", getAllPaths(rootNode));
        }
    }

    private boolean hasExtension(Path path) {
        return path.getFileName().toString().matches(".*\\.\\w{1,4}$");
    }

    private List<String> getAllPaths(JsonNode rootNode) {
        List<String> paths = new ArrayList<>();
        for (JsonNode node : rootNode) {
            paths.add("/" + stripStartSlash(node.get("path").asText()));
        }
        return paths;
    }

    private static String stripStartSlash(String path) {
        return path.startsWith("/") ? path.substring(1) : path;
    }

}
