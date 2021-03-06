/* LanguageTool, a natural language style checker
 * Copyright (C) 2013 Marcin Milkowski (http://www.languagetool.org)
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301
 * USA
 */
package org.languagetool.tokenizers.en;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.languagetool.tagging.en.EnglishTagger;
import org.languagetool.tokenizers.WordTokenizer;

/**
 * @author Marcin Milkowski
 * @since 2.5
 */
public class EnglishWordTokenizer extends WordTokenizer {

  private static final int maxPatterns = 4;
  private final Pattern[] patterns = new Pattern[maxPatterns];
  private final EnglishTagger tagger;

  @Override
  public String getTokenizingCharacters() {
    return super.getTokenizingCharacters() + "–"; // n-dash
  }

  public EnglishWordTokenizer() {

    tagger = new EnglishTagger();

    // words not to be split
    patterns[0] = Pattern.compile("^(fo['’]c['’]sle|rec['’]d|OK['’]d|cc['’]d)$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    // + not
    patterns[1] = Pattern.compile(
        "^(are|is|were|was|do|does|did|have|has|had|wo|would|ca|could|sha|should|must|ai|ought|might|need|may)(n['’]t)$",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    patterns[2] = Pattern.compile("^(.+)(['’]m|['’]re|['’]ll|['’]ve|['’]d|['’]s)(['’-]?)$",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    // split in two tokens
    patterns[3] = Pattern.compile("^(['’]t)(was)$",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  }

  /**
   * Tokenizes text. The English tokenizer differs from the standard one in two
   * respects:
   * <ol>
   * <li>it does not treat the hyphen as part of the word if the hyphen is at the
   * end of the word;</li>
   * <li>it includes n-dash as a tokenizing character, as it is used without a
   * whitespace in English.
   * </ol>
   * 
   * @param text String of words to tokenize.
   * @throws IOException
   */
  @Override
  public List<String> tokenize(String text) {
    List<String> l = new ArrayList<>();
    String auxText = text;

    auxText = auxText.replaceAll("'", "\u0001\u0001APOSTYPEW\u0001\u0001");
    auxText = auxText.replaceAll("’", "\u0001\u0001APOSTYPOG\u0001\u0001");
    //auxText = auxText.replaceAll("-", "\u0001\u0001HYPHEN\u0001\u0001");
    String s;
    String groupStr;

    final StringTokenizer st = new StringTokenizer(auxText, getTokenizingCharacters(), true);

    while (st.hasMoreElements()) {
      s = st.nextToken()
          .replaceAll("\u0001\u0001APOSTYPEW\u0001\u0001", "'")
          .replaceAll("\u0001\u0001APOSTYPOG\u0001\u0001", "’");
          //.replaceAll("\u0001\u0001HYPHEN\u0001\u0001", "-");
      boolean matchFound = false;
      int j = 0;
      Matcher matcher = null;
      while (j < maxPatterns && !matchFound) {
        matcher = patterns[j].matcher(s);
        matchFound = matcher.find();
        j++;
      }
      if (matchFound) {
        for (int i = 1; i <= matcher.groupCount(); i++) {
          groupStr = matcher.group(i);
          l.addAll(wordsToAdd(groupStr));
        }
      } else {
        l.addAll(wordsToAdd(s));
      }
    }
    return joinEMailsAndUrls(l);
  }

  /* Splits a word containing hyphen(-’') if it doesn't exist in the dictionary. */
  private List<String> wordsToAdd(String s) {
    final List<String> l = new ArrayList<>();
    synchronized (this) { // speller is not thread-safe
      if (!s.isEmpty()) {
        if (s.startsWith("-")) {
          l.add("-");
          l.addAll(wordsToAdd(s.substring(1)));
          return l;
        }
        if (s.endsWith("-")) {
          l.addAll(wordsToAdd(s.substring(0,s.length()-1)));
          l.add("-");
          return l;
        }
        if (!s.contains("-") && !s.contains("'") && !s.contains("’")) {
          l.add(s);
        } else {
          if (tagger.tag(Arrays.asList(s.replace("’", "'"))).get(0).isTagged()) {
            l.add(s);
          }
          // some camel-case words containing hyphen (is there any better fix?)
          else if (s.equalsIgnoreCase("mers-cov") || s.equalsIgnoreCase("mcgraw-hill")
              || s.equalsIgnoreCase("sars-cov-2") || s.equalsIgnoreCase("sars-cov") || s.equalsIgnoreCase("ph-metre")
              || s.equalsIgnoreCase("ph-metres") || s.equalsIgnoreCase("anti-ivg") || s.equalsIgnoreCase("anti-uv")
              || s.equalsIgnoreCase("anti-vih") || s.equalsIgnoreCase("al-qaida")) {
            l.add(s);
          } else {
            // if not found, the word is split
            //final StringTokenizer st2 = new StringTokenizer(s, "-’'", true);
            final StringTokenizer st2 = new StringTokenizer(s, "’'", true);
            while (st2.hasMoreElements()) {
              l.add(st2.nextToken());
            }
          }
        }
      }
      return l;
    }
  }
}
