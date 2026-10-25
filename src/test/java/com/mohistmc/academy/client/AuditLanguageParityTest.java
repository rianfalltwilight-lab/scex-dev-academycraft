package com.mohistmc.academy.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AuditLanguageParityTest {
    private static final Pattern FORMAT = Pattern.compile("%(?:[0-9]+\\$)?[sdif%]");
    @Test void threeLanguagesHaveSameKeysAndCompatiblePlaceholders() throws Exception {
        JsonObject english = load("en_us");
        for (String language : List.of("zh_cn", "ja_jp")) {
            JsonObject translated = load(language);
            assertEquals(english.keySet(), translated.keySet(), language);
            for (String key : english.keySet()) {
                String value = translated.get(key).getAsString();
                assertFalse(value.isBlank(), language + ":" + key);
                assertEquals(formats(english.get(key).getAsString()), formats(value), language + ":" + key);
                assertFalse(value.contains("\uFFFD"), language + ":" + key);
            }
        }
    }
    private static JsonObject load(String language) throws Exception {
        return JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/academy/lang/" + language + ".json"))).getAsJsonObject();
    }
    private static List<String> formats(String value) {
        var result = new ArrayList<String>();
        FORMAT.matcher(value).results().forEach(match -> result.add(match.group()));
        return result;
    }
}
