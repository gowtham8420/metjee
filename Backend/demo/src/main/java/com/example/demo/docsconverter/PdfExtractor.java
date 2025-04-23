package com.example.demo.docsconverter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class PdfExtractor {
    
    
    public static List<Map<String, Object>> extractItems(String latexContent) {
        List<Map<String, Object>> extractedItems = new ArrayList<>();

        // ✅ Fix regex to capture last \item as well
        Pattern pattern = Pattern.compile("\\\\item\\s*(.*?)(?=\\\\item|$)", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(latexContent);

        while (matcher.find()) {
            String extractedItem = matcher.group(1).trim().replace("\\\\", "\n");

            // ✅ Remove unwanted LaTeX commands
            extractedItem = extractedItem.replaceAll(
                "\\\\(begin|end)\\{.*?\\}|\\\\endhead|\\\\setcounter\\{enumi\\}\\{.*?\\}|\\\\def\\\\labelenumi\\{.*?\\}\\.?}",
                ""
            );
            
            // ✅ Remove \strut
            extractedItem = extractedItem.replaceAll("\\\\strut", "").trim();
            
            extractedItem = extractedItem.replaceAll("\\\\emph\\{(.*?)\\}", "$1");

            // Replace \( with \[ and \) with \]
            extractedItem = extractedItem.replaceAll("\\\\\\(", "\\\\[")
                                 .replaceAll("\\\\\\)", "\\\\]");

            System.out.println(extractedItem);
//            // ✅ Replace superscripts & subscripts
            extractedItem = replaceLatexSuperscriptAndSubscript(extractedItem);

            // ✅ Extract question and options (Fixes potential incorrect parsing)
            Pattern questionPattern = Pattern.compile(
                "(?s)(.*?)(?=\\s*a\\))\\s*a\\)(.*?)\\s*b\\)(.*?)\\s*c\\)(.*?)\\s*d\\)(.*?)$"
            );
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
    // ✅ Fix formatting for MathJax
    private static String formatForMathJax(String input) {
        return input.replaceAll("\\\\\\\\frac", "\\frac")
                    .replaceAll("\\s+", " "); // Normalize spaces
    }
       
       // Function to replace LaTeX superscripts and subscripts
       private static String replaceLatexSuperscriptAndSubscript(String text) {
//    	   text = text.replaceAll("\\\\(", "\\[");

//    	   text = text.replaceAll("\\\\)", "\\\\]");

     // Convert superscripts to block LaTeX format
       	text = text.replaceAll("(\\b\\w+)\\\\textsuperscript\\{(.*?)\\}", "\\\\[ $1^{ $2 } \\\\]");

       	// Convert subscripts to block LaTeX format
       	text = text.replaceAll("(\\b\\w+)\\\\textsubscript\\{(.*?)\\}", "\\\\[ $1_{ $2 } \\\\]");


           return text;
       }
       public static String readLatexFile(String filePath) throws IOException {
           return Files.readString(Path.of(filePath));
       }


}

