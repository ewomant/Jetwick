/**
 * Copyright (C) 2010 Peter Karich <jetwick_@_pannous_._info>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.jetwick.data;

import de.jetwick.tw.TweetDetector;
import de.jetwick.tw.Twitter4JTweet;
import de.jetwick.tw.cmd.StringFreqMap;
import de.jetwick.util.Helper;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import twitter4j.Tweet;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class JTweet implements ElasticObject<JTweet>, Serializable {

    private static final long serialVersionUID = 1L;
    public static final int MAX_LENGTH = 800;
    public static final Comparator tweetIdComparator = new TwitterIdComparator();
    public static final int QUAL_MAX = 100;
    //
    // detect three other *similar* tweets THEN BAD
    // (LOW/100)^2 = 0.5625; (LOW/100)^3 = 0.4219 < BAD/100
    public static final int QUAL_LOW = 75;
    //
    // detect two other nearly *identical* tweet THEN SPAM
    // (BAD/100)^2 = 0.25 < SPAM/100
    public static final int QUAL_BAD = 50;
    public static final int QUAL_SPAM = 26;
    private final long twitterId;
    private String text;
    private Set<JTweet> replies = new LinkedHashSet<JTweet>();
    private int retweetCount;
    private boolean retweet = false;
    private boolean daemon = false;
    private long version;
    private Date createdAt;
    private Date updatedAt;
    private JUser fromUser;
    private JTweet inReplyOf;
    private long inReplyTwitterId = -1L;
    private String location;
    private StringFreqMap textTerms = new StringFreqMap(8);
    private StringFreqMap languages = new StringFreqMap(4);
    private String language = TweetDetector.UNKNOWN_LANG;
    private int quality;
    private String lowerCaseText;
    private List<UrlEntry> urlEntries;
    private int replyCount;
    private String qualDebug;
    private int qualReductions = 0;
    private Collection<Long> duplicates = new LinkedHashSet<Long>(4);
    private Date instantiatedAt = new Date();
    private String feedSource;
    private double latitude;
    private double longitude;
    private int updateCount;
    private boolean isProtected = false;

    /**
     * You'll need to call init after that constructor
     */
    public JTweet(Tweet tw, JUser fromUser) {
        this(tw);
        setFromUser(fromUser);
    }

    public JTweet(long id, String text, JUser fromUser) {
        this(id, text, new Date());
        setFromUser(fromUser);
    }

    /**
     * For tests only! Use contructor instead which initialized user too!
     */
    public JTweet(Tweet tw) {
        this(tw.getId(), tw.getText(), tw.getCreatedAt());

        // if tweet was retrieved via Status object
        if (tw instanceof Twitter4JTweet) {
            Twitter4JTweet myTw = (Twitter4JTweet) tw;
            inReplyTwitterId = myTw.getInReplyToStatusId();
            urlEntries = myTw.getUrlEntries();
        }

        // most tweets have location == null. See user.location
        if (tw.getGeoLocation() != null)
            setGeoLocation(tw.getGeoLocation().getLatitude(),
                    tw.getGeoLocation().getLongitude());

        location = tw.getLocation();
    }

    /**
     * for tests only
     */
    public JTweet(long id, String text, Date createdAt) {
        quality = QUAL_MAX;
        this.twitterId = id;
        setText_(text);
        this.createdAt = createdAt;

        if (urlEntries == null)
            urlEntries = new ArrayList<UrlEntry>(1);
    }

    public JTweet addUrlEntry(UrlEntry ue) {
        urlEntries.add(ue);
        return this;
    }

    public Collection<UrlEntry> getUrlEntries() {
        return urlEntries;
    }

    public void setUrlEntries(Collection<UrlEntry> entries) {
        getUrlEntries().clear();
        getUrlEntries().addAll(entries);
    }

    @Override
    public long getVersion() {
        return version;
    }

    @Override
    public JTweet setVersion(long version) {
        if (version < 0)
            throw new IllegalStateException("version cannot be negative:" + version);

        this.version = version;
        return this;
    }

    public int getUpdateCount() {
        return updateCount;
    }

    public JTweet setUpdateCount(int updateCount) {
        this.updateCount = updateCount;
        return this;
    }

    public String getLowerCaseText() {
        if (lowerCaseText == null)
            lowerCaseText = getText().toLowerCase();

        return lowerCaseText;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public StringFreqMap getLanguages() {
        return languages;
    }

    public void setLanguages(StringFreqMap languages) {
        this.languages = languages;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public StringFreqMap getTextTerms() {
        return textTerms;
    }

    public void setTextTerms(StringFreqMap textTerms) {
        this.textTerms = textTerms;
    }

    public long getInReplyTwitterId() {
        return inReplyTwitterId;
    }

    public JTweet setInReplyTwitterId(long inReplyTwitterId) {
        this.inReplyTwitterId = inReplyTwitterId;
        return this;
    }

    public Long getTwitterId() {
        return twitterId;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public JTweet setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public JTweet setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
        return this;
    }

    public JTweet makePersistent() {
        setUpdatedAt(new Date());
        return this;
    }

    /**
     * @return false if this tweet should be deleted after some days
     */
    public boolean isPersistent() {
        return updatedAt != null;
    }

    public void setFromUser(JUser fromUser, boolean reverse) {
        this.fromUser = fromUser;
        if (reverse)
            fromUser.addOwnTweet(this, false);
    }

    public JTweet setFromUser(JUser fromUser) {
        setFromUser(fromUser, true);
        return this;
    }

    public JUser getFromUser() {
        return fromUser;
    }

    public void setReplyCount(int rp) {
        this.replyCount = rp;
    }

    public JTweet addReply(JTweet tw) {
        replies.add(tw);
        tw.setInReplyOf(this);
        return this;
    }

    public int getReplyCount() {
        // TODO better design! (do not mix count and replies)
        return replyCount + replies.size();
    }

    public JTweet getInReplyOf() {
        return inReplyOf;
    }

    public void setInReplyOf(JTweet inReplyOf) {
        this.inReplyOf = inReplyOf;
        if (inReplyOf == null)
            inReplyTwitterId = -1L;
        else
            inReplyTwitterId = inReplyOf.getTwitterId();
    }

    public JTweet setRetweetCount(int rt) {
        this.retweetCount = rt;
        return this;
    }

    public int getRetweetCount() {
        // TODO better design! (do not mix count and replies)
        int tmp = 0;
        for (JTweet tw : replies) {
            if (tw.isRetweet())
                tmp++;
        }
        return retweetCount + tmp;
    }

    public String getText() {
        return text;
    }

    private void setText_(String t) {
        text = t;
        // skip none-utf8 characters, otherwise we have major problems while
        // querying solr
        this.text = Helper.xmlCharacterWhitelist(text);
        retweet = getLowerCaseText().contains("rt @");
    }

    public boolean isRetweet() {
        return retweet;
    }

    public String extractRTText() {
        int index1 = getLowerCaseText().indexOf("rt @");
        if (index1 < 0)
            return "";

        index1 = getText().indexOf(" ", index1 + 4);
        if (index1 < 0)
            return "";

        return getText().substring(index1 + 1).trim();
    }

    public boolean isRetweetOf(JTweet tw) {
        // e.g. return true if this.text == RT @userA: text
        // to lower case is necessary because the case of the fromUser isn't important
        if (!isRetweet())
            return false;

        String thisT = getLowerCaseText();
        String extT = tw.getLowerCaseText();
        return thisT.contains("rt @" + tw.getFromUser() + ": " + extT) || thisT.contains("rt @" + tw.getFromUser() + " " + extT);
//        return thisT.matches(".*rt @" + tw.getFromUser() + ":? " + extT + ".*");
    }

    public JTweet setDaemon(boolean daemon) {
        this.daemon = daemon;
        return this;
    }

    /**
     * If a tweet is added to the system and it is a retweet but no original
     * tweet can be found a daemon tweet will be created to reflect this
     * missing tweet.
     *
     * daemon tweets are expensive to look for and only 0.3% of the tweets (!)
     * are only reactivated daemon tweets!
     */
    public boolean isDaemon() {
        return daemon;
    }

    public int getQuality() {
        return quality;
    }

    public JTweet multiplyQuality(double factor) {
        quality *= factor;
        return this;
    }

    public JTweet setQuality(int quality) {
        this.quality = quality;
        return this;
    }

    public boolean isSpam() {
        return quality < JTweet.QUAL_SPAM && quality >= 0;
    }

    /**
     * For debugging purposes
     */
    public void addQualAction(String str) {
        if (qualDebug == null)
            qualDebug = str;
        else
            qualDebug += str;

        qualReductions++;
    }

    public String getQualDebug() {
        return qualDebug;
    }

    public int getQualReductions() {
        return qualReductions;
    }

    public static boolean isDefaultInReplyId(long inReplyTwitterId) {
        return inReplyTwitterId == -1;
    }

    /**
     * skip tweets with identical id or identical text. For the latter case:
     * greater ids will win and identical text is only skipped if there is no
     * tweet in-between. see the test case
     */
    public static void deduplicate(List<JTweet> list) {
        // now remove tweets if they have the identical twitterId or text.
        // the standard hashCode/equals are based on the twitterId only
        Iterator<JTweet> iter = list.iterator();
        JTweet prevTweet = null;
        while (iter.hasNext()) {
            JTweet tw = iter.next();
            if (prevTweet != null && (tw.getTwitterId().equals(prevTweet.getTwitterId())
                    || tw.getText().equals(prevTweet.getText()))) {
                iter.remove();
            }
            prevTweet = tw;
        }
    }

    public static void sortAndDeduplicate(List<JTweet> list) {
        Collections.sort(list, tweetIdComparator);
        deduplicate(list);
    }

    @Override
    public int hashCode() {
        return 67 * 5 + (int) (this.twitterId ^ (this.twitterId >>> 32));
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || getClass() != obj.getClass())
            return false;

        return this.twitterId == ((JTweet) obj).twitterId;
    }

    @Override
    public String toString() {
        return twitterId + " " + createdAt + " " + text + " v" + getVersion();
    }
    public static final Map<String, Set<String>> NOISE_WORDS = new LinkedHashMap<String, Set<String>>();
    public static final Map<String, Set<String>> LANG_DET_WORDS = new LinkedHashMap<String, Set<String>>();
    public static final Set<String> NOISE_WORDS_SINGLE = new LinkedHashSet<String>(Arrays.asList(new String[]{
                "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z"
            }));
    public static final Set<String> NOISE_WORDS_NUM = new LinkedHashSet<String>(Arrays.asList(new String[]{
                "00", "01", "02", "03", "04", "05", "06", "07", "08", "09",
                "1", "10", "100", "11", "12", "13", "14", "15", "16", "17",
                "18", "19", "2", "20", "21", "22", "23", "24", "25", "26",
                "27", "28", "29", "3", "30", "31", "32", "33", "34", "35",
                "36", "37", "38", "39", "4", "40", "41", "42", "43", "44",
                "45", "46", "47", "48", "49", "5", "50", "51", "52", "53",
                "54", "55", "56", "57", "58", "59", "6", "60", "61", "62",
                "63", "64", "65", "66", "67", "68", "69", "7", "70", "71",
                "72", "73", "74", "75", "76", "77", "78", "79", "8", "80",
                "81", "82", "83", "84", "85", "86", "87", "88", "89", "9",
                "90", "91", "92", "93", "94", "95", "96", "97", "98", "99", "100", "000"}));
    public static final Set<String> NOISE_WORDS_MISC = new LinkedHashSet<String>(Arrays.asList(new String[]{
                // ### TWITTER
                "ah",
                "aw", "cu", "ff",
                "haha", "hahaha", "hehe", "hey", "hi",
                "pls",
                "rt", "re", "soo", "thx",
                "yeah", "via",
                "/by", "/cc", "/via",
                "+1", "-1", ";d", "^^",
                // ### MISC
                ".", ",", ";", "ur", "tx", "ini", "ii", "iii",
                "//", "\\n", "\n", "com", "de", "el", "en", "je", "jp", "lol",
                "ne", "om", "ve", "ya", "yr", "za"
            }));
    // ### Ausländisch ###
    public static final Set<String> NOISE_WORDS_UNSORTED = new LinkedHashSet<String>(Arrays.asList(new String[]{
                "¿qué",
                "ak", "aku", "aja", "al", "ada", "amb", "así", "au", "avec",
                "δεν",
                "bien", "boa", "bom", "bueno",
                "ca", "ça", "cap", "ce", "c'est", "cek", "ces", "che", "chi", "ci",
                "col", "com", "como", "con", "crec", "cosa", "cuando", "cumpleaños",
                "dan", "dans", "dc", "del", "decir", "dólar", "dong", "dua",
                "di",
                "ed", "een", "ei", "el", "els", "em", "en", "entre", "era", "és", "est",
                "está", "esta", "estes", "estoy", "eso", "et", "été", "ex",
                "fer", "fu",
                "ga", "ge", "gue",
                "ha", "hay", "han", "het", "ho", "hoy",
                "ik",
                "il", " inte", "iv",
                "jajaja", "je", "jo", "jos", "ju",
                "και",
                "ki",
                "ke",
                "la", "las", "le", "les", "lett", "leur", "li", "lo", "los",
                "mas", "más",
                "mejor", "més", "merci", "ma", "me", "mi", "mon", "muchas", "muy",
                "με",
                "não", "nada",
                "ne", "ni", "nih", "non", "nor", "nos", "notre", "nu",
                "nya",
                "ga", "gracias", "gua", "guau",
                "θα",
                "opció", "ou", "oui",
                "par", "para", "pas", "per", "pero", "por", "pour", "pro",
                "qualche", "que", "qu", "qui",
                "san",
                "se", "sen", "ses", "sí", "si", "sin",
                "sólo", "son", "somme", "soirée", "sous",
                "su", "suis", "sul", "sur", "sus",
                "ta", "també", "te", "té", "tem", "ti", "tinc", "tion", "tive", "todos", "το", "tous", "tra", "très", "tu",
                "uma", "un", "una", "une", "ut",
                "va", "van", "να", "vi", "vie", "vos", "vous", "votre",
                "yang", "για", "yg", "yo", "qué"}));
    public static final Set<String> PHRASE_WHITE_LIST = new LinkedHashSet<String>(Arrays.asList(new String[]{
                "bin laden", // -> otherwise wrong language detection for 'alqaedatracker' because of 'bin'
                "open source"
            }));

    static {
        // fill collection for language detection
        for (String lang : TweetDetector.LANGS) {
            importFrom(LANG_DET_WORDS, lang);
        }

        // fill collection for noise word determination
        for (String lang : TweetDetector.LANGS) {
            importNoiseFrom(NOISE_WORDS, lang);
        }

//        int delta = LANG_DET_WORDS.size();
        for (Entry<String, Set<String>> noiseTerms : NOISE_WORDS.entrySet()) {
            addFrom(LANG_DET_WORDS, noiseTerms);
        }
        //System.out.println("added " + (LANG_DET_WORDS.size() - delta) + " terms to lang detection from noise terms");

        addFrom(NOISE_WORDS, TweetDetector.UNKNOWN_LANG, NOISE_WORDS_UNSORTED);

        // indifferent
        addFrom(NOISE_WORDS, TweetDetector.MISC_TERMS, NOISE_WORDS_MISC);
        addFrom(NOISE_WORDS, TweetDetector.SINGLE_CHAR_TERMS, NOISE_WORDS_SINGLE);
        addFrom(NOISE_WORDS, TweetDetector.NUM_TERMS, NOISE_WORDS_NUM);
    }

    public static void importNoiseFrom(Map<String, Set<String>> words, String lang) {
        try {
            List<String> list = Helper.readFile(Helper.createBuffReader(JTweet.class.getResourceAsStream("noise_words_" + lang + ".txt")));
            addFrom(words, lang, list);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public static void importFrom(Map<String, Set<String>> words, String lang) {
        try {
            List<String> list = Helper.readFile(Helper.createBuffReader(JTweet.class.getResourceAsStream("lang_det_" + lang + ".txt")));
            addFrom(words, lang, list);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public static void addFrom(Map<String, Set<String>> words, String lang, Collection<String> collection) {
        for (String str : collection) {
            if (str.isEmpty() || str.startsWith("//"))
                continue;

            str = str.trim().toLowerCase();
            Set<String> langs = words.get(str);
            if (langs == null)
                langs = new LinkedHashSet<String>(10);

            langs.add(lang);
            words.put(str, langs);
        }
    }

    public static void addFrom(Map<String, Set<String>> words, Entry<String, Set<String>> entry) {
        String str = entry.getKey();
        if (str.isEmpty() || str.startsWith("//"))
            return;

        str = str.trim().toLowerCase();
        Set<String> langs = words.get(str);
        if (langs == null)
            langs = new LinkedHashSet<String>(10);

        langs.addAll(entry.getValue());
        words.put(str, langs);
    }

    /**
     * specifies how many existing tweets with similar content were found
     */
    public Collection<Long> getDuplicates() {
        return duplicates;
    }

    public void addDuplicate(long twId) {
        duplicates.add(twId);
    }

    public int getQueueAgeInSeconds() {
        return Math.round((System.currentTimeMillis() - instantiatedAt.getTime()) / 1000f);
    }

    public JTweet setFeedSource(String feedSource) {
        this.feedSource = feedSource;
        return this;
    }

    public String getFeedSource() {
        return feedSource;
    }

    @Override
    public String getId() {
        return Long.toString(getTwitterId());
    }

    public JTweet setGeoLocation(double lat, double lon) {
        latitude = lat;
        longitude = lon;
        return this;
    }

    /**
     * @return latitude
     */
    public double getLat() {
        return latitude;
    }

    /**
     * @return longitude
     */
    public double getLon() {
        return longitude;
    }

    /**
     * This method specifies how this tweet should get updated from a
     * tweet - either an out-of-date tweet fetched from index or otherway around
     */
    @Override
    public JTweet updateFrom(JTweet a) {
        if (!getId().equals(a.getId()))
            throw new IllegalStateException("ids have to be the same to call update. This:" + this + " update:" + a);

        if (getRetweetCount() > a.getRetweetCount())
            return this;

        setReplyCount(a.replyCount);
        setRetweetCount(a.retweetCount);
        replies.clear();
        for (JTweet repl : a.replies) {
            addReply(repl);
        }
        duplicates.clear();
        for (Long val : a.getDuplicates()) {
            addDuplicate(val);
        }
        return this;
    }

    public JTweet setProtected(boolean aProtected) {
        isProtected = aProtected;
        return this;
    }

    public boolean isProtected() {
        return isProtected;
    }

    public String getUrl() {
        if (getUrlEntries() == null || getUrlEntries().isEmpty())
            return null;

        return getUrlEntries().iterator().next().getResolvedUrl();
    }
}