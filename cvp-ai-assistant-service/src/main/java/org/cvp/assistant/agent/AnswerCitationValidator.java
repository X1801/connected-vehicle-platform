package org.cvp.assistant.agent;

import org.cvp.assistant.domain.evidence.EvidenceSource;
import org.cvp.assistant.domain.evidence.ToolOutcome;
import org.cvp.assistant.rag.RagPassage;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bounded P0 guard: citation membership plus obvious maintenance status contradictions.
 */
final class AnswerCitationValidator {
    private static final Pattern CITATION = Pattern.compile("\\[([A-Za-z]+\\d+)]");
    private static final Pattern STATUS = Pattern.compile("status=([A-Z_]+)");
    private static final Pattern FACT = Pattern.compile("([A-Za-z][A-Za-z0-9]*)=([^,\\n]+)");
    private static final Pattern ODOMETER_CLAIM = Pattern.compile("(?i)\\b(?:odometer|mileage)\\w*\\s*(?:is|of|:|=)?\\s*([0-9][0-9,]*)");
    private static final Pattern DUE_DATE_CLAIM = Pattern.compile("(?i)\\bdue(?:\\s+date)?\\s*(?:is|on|:|=)?\\s*(\\d{4}-\\d{2}-\\d{2})");
    private static final Pattern MODEL_CLAIM = Pattern.compile("(?i)\\bmodel(?:\\s+code)?\\s*(?:is|:|=)?\\s*([A-Za-z0-9-]+)");

    private AnswerCitationValidator() {
    }

    static Result validatedOrFallback(String question, String answer, List<ToolOutcome> outcomes, List<RagPassage> passages) {
        Set<String> known = new HashSet<>();
        outcomes.stream().filter(outcome -> outcome.status() == ToolOutcome.Status.SUCCESS)
                .forEach(outcome -> outcome.sources().forEach(source -> known.add(source.evidenceId())));
        passages.forEach(passage -> known.add(passage.source().evidenceId()));
        Set<String> cited = citations(answer);
        if (known.isEmpty())
            return new Result(requiresBusinessEvidence(question) ? insufficientEvidence() : generalAnswer(), List.of(), true);
        if (cited.isEmpty() || cited.stream().anyMatch(id -> !known.contains(id)) || answer.matches("(?s).*\\b[Ss]ource\\s*:.*") || contradictsMaintenanceReminder(answer, outcomes) || contradictsNamedFacts(answer, outcomes)) {
            String fallback = fallback(outcomes, passages);
            return new Result(fallback, new ArrayList<>(citations(fallback)), true);
        }
        return new Result(answer, new ArrayList<>(cited), false);
    }

    private static Set<String> citations(String answer) {
        Set<String> ids = new LinkedHashSet<>();
        Matcher matcher = CITATION.matcher(answer);
        while (matcher.find()) ids.add(matcher.group(1));
        return ids;
    }

    private static boolean contradictsMaintenanceReminder(String answer, List<ToolOutcome> outcomes) {
        Set<String> actual = new HashSet<>();
        outcomes.stream().filter(outcome -> outcome.status() == ToolOutcome.Status.SUCCESS && outcome.tool().equals("getMaintenanceStatus"))
                .forEach(outcome -> {
                    Matcher m = STATUS.matcher(outcome.facts());
                    while (m.find()) actual.add(m.group(1));
                });
        if (actual.isEmpty()) return false;
        for (String status : List.of("COMPLETED", "SCHEDULED", "PENDING", "ACKNOWLEDGED", "DISMISSED", "EXPIRED")) {
            if (answer.toUpperCase(Locale.ROOT).contains(status) && !actual.contains(status)) return true;
        }
        return false;
    }

    private static boolean contradictsNamedFacts(String answer, List<ToolOutcome> outcomes) {
        Map<String, Set<String>> facts = new HashMap<>();
        outcomes.stream().filter(outcome -> outcome.status() == ToolOutcome.Status.SUCCESS).forEach(outcome -> {
            Matcher matcher = FACT.matcher(outcome.facts());
            while (matcher.find())
                facts.computeIfAbsent(matcher.group(1), ignored -> new HashSet<>()).add(normalize(matcher.group(2)));
        });
        return contradicts(answer, ODOMETER_CLAIM, facts.get("odometerKm"), true)
                || contradicts(answer, DUE_DATE_CLAIM, facts.get("dueDate"), false)
                || contradicts(answer, MODEL_CLAIM, facts.get("model"), false);
    }

    private static boolean contradicts(String answer, Pattern claim, Set<String> actual, boolean numeric) {
        if (actual == null || actual.isEmpty()) return false;
        Matcher matcher = claim.matcher(answer);
        if (actual.size() > 1 && matcher.find()) return true;
        matcher.reset();
        while (matcher.find()) {
            String value = normalize(matcher.group(1));
            if (numeric) value = value.replace(",", "");
            if (!actual.contains(value)) return true;
        }
        return false;
    }

    private static String normalize(String value) {
        return value.replace("\"", "").trim().toUpperCase(Locale.ROOT);
    }

    private static String fallback(List<ToolOutcome> outcomes, List<RagPassage> passages) {
        for (ToolOutcome outcome : outcomes)
            if (outcome.status() == ToolOutcome.Status.SUCCESS && !outcome.sources().isEmpty())
                return "I can confirm only the available backend evidence; I cannot safely make a more specific claim. [" + outcome.sources().getFirst().evidenceId() + "]";
        if (!passages.isEmpty())
            return "I found approved knowledge that may explain the topic, but it does not confirm current vehicle status. [" + passages.getFirst().source().evidenceId() + "]";
        return insufficientEvidence();
    }

    static boolean requiresBusinessEvidence(String question) {
        String normalized = question == null ? "" : question.trim().toLowerCase(Locale.ROOT);
        return !normalized.matches("(?:hi|hello|hey|bonjour|thanks|thank you|help|what can you do)[!?. ]*");
    }

    private static String insufficientEvidence() {
        return "I could not retrieve verified evidence needed to confirm the vehicle maintenance information. I cannot safely state maintenance status, reminders, mileage, due dates, model, or capabilities.";
    }

    private static String generalAnswer() {
        return "Hello. I can help explain verified vehicle maintenance information when supporting evidence is available.";
    }

    record Result(String answer, List<String> citationIds, boolean fallback) {
    }
}
