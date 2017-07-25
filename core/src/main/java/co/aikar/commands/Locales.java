/*
 * Copyright (c) 2016-2017 Daniel Ennis (Aikar) - MIT License
 *
 *  Permission is hereby granted, free of charge, to any person obtaining
 *  a copy of this software and associated documentation files (the
 *  "Software"), to deal in the Software without restriction, including
 *  without limitation the rights to use, copy, modify, merge, publish,
 *  distribute, sublicense, and/or sell copies of the Software, and to
 *  permit persons to whom the Software is furnished to do so, subject to
 *  the following conditions:
 *
 *  The above copyright notice and this permission notice shall be
 *  included in all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 *  EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 *  MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 *  NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 *  LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 *  OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 *  WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package co.aikar.commands;

import co.aikar.locales.LocaleManager;
import co.aikar.locales.MessageKey;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;

@SuppressWarnings("WeakerAccess")
public class Locales {
    private final CommandManager<?, ?, ?> manager;
    private final LocaleManager<CommandIssuer> localeManager;
    private final SetMultimap<String, Locale> loadedBundles = HashMultimap.create();

    public Locales(CommandManager manager) {
        this.manager = manager;
        this.localeManager = LocaleManager.create(manager::getIssuerLocale);
    }

    public void loadLanguages() {
        addMessageBundles("acf-core");
    }

    public Locale getDefaultLocale() {
        return this.localeManager.getDefaultLocale();
    }

    public Locale setDefaultLocale(Locale locale) {
        return this.localeManager.setDefaultLocale(locale);
    }

    /**
     * Looks for all previously loaded bundles, and if any new Supported Languages have been added, load them.
     */
    public void loadMissingBundles() {
        for (Locale locale : manager.getSupportedLanguages()) {
            for (String bundleName : loadedBundles.keys()) {
                addMessageBundle(bundleName, locale);
            }
        }
    }

    public void addMessageBundles(String... bundleNames) {
        for (String bundleName : bundleNames) {
            for (Locale locale : manager.getSupportedLanguages()) {
                addMessageBundle(bundleName, locale);
            }
        }
    }

    public void addMessageBundle(String bundleName, Locale locale) {
        if (!loadedBundles.containsEntry(bundleName, locale)) {
            loadedBundles.put(bundleName, locale);
            this.localeManager.addMessageBundle(bundleName, locale);
        }
    }

    public void addMessageStrings(Locale locale, @NotNull Map<String, String> messages) {
        Map<MessageKey, String> map = new HashMap<>(messages.size());
        messages.forEach((key, value) -> map.put(MessageKey.of(key), value));
        addMessages(locale, map);
    }
    public void addMessages(Locale locale, @NotNull Map<MessageKey, String> messages) {
        this.localeManager.addMessages(locale, messages);
    }

    public String addMessage(Locale locale, MessageKey key, String message) {
        return this.localeManager.addMessage(locale, key, message);
    }

    public String getMessage(CommandIssuer issuer, MessageKey key) {
        String message = this.localeManager.getMessage(issuer, key);
        if (message == null) {
            manager.log(LogLevel.ERROR, "Missing Language Key: " + key.getKey());
            message = "<MISSING_LANGUAGE_KEY:" + key.getKey() + ">";
        }
        return message;
    }

    public String replaceI18NStrings(String message) {
        if (message == null) {
            return null;
        }
        Matcher matcher = ACFPatterns.I18N_STRING.matcher(message);
        if (!matcher.matches()) {
            return message;
        }

        CommandIssuer issuer = CommandManager.getCurrentCommandIssuer();

        matcher.reset();
        StringBuffer sb = new StringBuffer(message.length());
        while (matcher.find()) {
            MessageKey key = MessageKey.of(matcher.group("key"));
            matcher.appendReplacement(sb, Matcher.quoteReplacement(getMessage(issuer, key)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
