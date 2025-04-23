package com.example.demo;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.demo.docsconverter.PdfExtractor;

import reactor.core.publisher.Flux;

@RestController
@CrossOrigin
public class ChatController {
	
	
    @Autowired
    public PdfExtractor PdfExtractor;

    private final OllamaChatModel chatModel;
    
    private static final String UPLOAD_DIR = "uploads/";
    

    @Autowired
    public ChatController(OllamaChatModel chatModel) {
        this.chatModel = chatModel;
    }
    
    @GetMapping("/docdata")
    public ResponseEntity<?> getDocData(){
    	String docxFilePath = "onemark.docx"; // Change to your file path
        File filse = new File(docxFilePath);

        if (!filse.exists() || !filse.isFile()) {
            return ResponseEntity.status(404).body("File not found at: " + docxFilePath);
        }
       
        try (FileInputStream fis = new FileInputStream(filse);
                XWPFDocument document = new XWPFDocument(fis);
        		 XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
        	 String[] lines = extractor.getText().split("\\n"); // Read file line by line
        	 JSONArray questionList = new JSONArray();
        	 LinkedHashMap<String, String> questionObj = null;
             String currentQuestion = null;

             long questionCount=1;
             for (String line : lines) {
                 line = line.trim();
                 if (line.isEmpty()) continue; // Ignore empty lines
                 
                 if (line.matches("[a-d]\\).*")) { // Matches options (a) b) c) d))
                     String optionKey = line.substring(0, 1); // Extract "a", "b", etc.
                     String optionValue = line.substring(2).trim(); // Extract the option text
                     if (questionObj != null) {
                         questionObj.put(optionKey, optionValue);
                     }
                 } else { // If not an option, it's a question
                     if (questionObj != null) {
                    	 questionList.put(new JSONObject(questionObj)); // Save previous question
                    	 questionCount++;
                     }
                     questionObj = new LinkedHashMap();
                     questionObj.put("question", line); // Store question text            
                 }
             }

          // Add the last question
             if (questionObj != null) {
            	 questionList.put(new JSONObject(questionObj));
             }
             // Convert list to JSON output
             JSONObject finalJson = new JSONObject();
             finalJson.put("questions", questionList);
             System.out.println(questionCount); // Pretty print JSON
             return ResponseEntity.ok(finalJson.toString());

           } catch (IOException e) {
               e.printStackTrace();
               return ResponseEntity.status(500).body("Error reading the document file.");
           }
    
    }
    @PostMapping("/upload")
    public ResponseEntity<?> handleFileUpload(@RequestParam("file") MultipartFile file){
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("{\"message\": \"No file selected!\"}");
        }
        
        try {
            // Ensure directory exists
            File uploadDir = new File(UPLOAD_DIR);
            if (!uploadDir.exists()) {
                uploadDir.mkdirs();
            }

            // Save file
            String originalFilename = file.getOriginalFilename();
            String filePath = UPLOAD_DIR + originalFilename;
            File savedFile = new File(filePath);
         // Use Java NIO for safe file writing
            Files.write(savedFile.toPath(), file.getBytes(), StandardOpenOption.CREATE);
            	// below logic is for mathematical equation//
//            String hashValue = generateSHA256Hash(originalFilename);
            String baseName = originalFilename.substring(0, originalFilename.lastIndexOf(".")); 
            String hashValue = "convert";
            String outputFilename =baseName + "_" + hashValue + ".tex";
            String outputFolder =baseName + "_" + hashValue;
            // Execute Pandoc command
            ProcessBuilder processBuilder = new ProcessBuilder(
                "pandoc", originalFilename, "-o", outputFilename, "--extract-media=" + outputFolder
            );
            
         // Set the working directory to UPLOAD_DIR
            processBuilder.directory(new File(UPLOAD_DIR));
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();

            
            String LatexfilePath = UPLOAD_DIR + outputFilename;
            String latexContent = readLatexFile(LatexfilePath);
            return ResponseEntity.ok(PdfExtractor.extractItems(latexContent));


        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("{\"message\": \"Upload failed!\"}");
}
    }


    
    
    @GetMapping("/view")
    public List<Map<String, Object>> getQuestions() throws IOException {
    	String filePath = "questionintegralsqurt.tex";
        String latexContent = readLatexFile(filePath);
        return PdfExtractor.extractItems(latexContent);
    }
    
    
    private String readLatexFile(String filePath) throws IOException {
        return Files.readString(Path.of(filePath));
    }
    
    private List<Map<String, Object>> extractItems(String latexContent) {
        List<Map<String, Object>> extractedItems = new ArrayList<>();
        
        // Regular expression to match content between \item occurrences
        Pattern pattern = Pattern.compile("\\\\item\\s*(.*?)(?=\\\\item|$)", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(latexContent);

        while (matcher.find()) {
            String extractedItem = matcher.group(1).trim().replace("\\\\", "\n");

            // Remove unnecessary LaTeX elements
            extractedItem = extractedItem.replaceAll(
                "\\\\(begin|end)\\{.*?\\}|\\\\endhead|\\\\setcounter\\{enumi\\}\\{.*?\\}|\\\\def\\\\labelenumi\\{.*?\\}\\.?}",
                ""
            );

            // Extract question and options
            Pattern questionPattern = Pattern.compile("(?s)(.*?)(?=\\s*a\\))\\s*a\\)(.*?)\\s*b\\)(.*?)\\s*c\\)(.*?)\\s*d\\)(.*?)$");
            Matcher questionMatcher = questionPattern.matcher(extractedItem);


            if (questionMatcher.find()) {
                Map<String, Object> questionData = new HashMap<>();
                questionData.put("question", formatForMathJax(questionMatcher.group(1).trim()));
                questionData.put("optionA", formatForMathJax(questionMatcher.group(2).trim()));
                questionData.put("optionB", formatForMathJax(questionMatcher.group(3).trim()));
                questionData.put("optionC", formatForMathJax(questionMatcher.group(4).trim()));
                questionData.put("optionD", formatForMathJax(questionMatcher.group(5).trim()));
                extractedItems.add(questionData);
            }
        }
        return extractedItems;
    }
    private String formatForMathJax(String text) {
        return "\\(" + text.replace(" ", " \\ ") + "\\)"; // Adds LaTeX spacing for better rendering
    }
}