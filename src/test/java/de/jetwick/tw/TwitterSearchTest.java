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
package de.jetwick.tw;

import de.jetwick.util.Helper;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class TwitterSearchTest {

    public TwitterSearchTest() {
    }

    @Test
    public void testUtf8NotXmlCompatible() {
        // takem from http://twitter.com/szeyan1220/status/14805768527
        String str = "@aiww ￿装逼犯们听着";
        String str2 = Helper.xmlCharacterWhitelist(str);
        assertTrue(str.length() > str2.length());
    }

//    public TwitterSearch createTwitter() {
//        return new TwitterSearch() {
//
//            @Override
//            public Twitter createTwitter() {
//                return new Twitter() {
//
//                    @Override
//                    public ResponseList<Status> getUserTimeline(String screenName, Paging paging)
//                            throws TwitterException {
//
//                    }
//                };
//            }
//
//            @Override
//            public int getRateLimit() {
//                return 150;
//            }
//
//            @Override
//            public Collection<Tweet> updateUserInfo(List<? extends YUser> users) {
//                return Collections.EMPTY_LIST;
//            }
//        }.setCredits(new Credits());
//    }

    @Test
    public void testLocation() {
        assertEquals("Berlin, -", TwitterSearch.toStandardLocation("Berlin"));
        assertEquals("Berlin, Germany", TwitterSearch.toStandardLocation("Berlin, Germany"));
        assertEquals("Mountain View, CA", TwitterSearch.toStandardLocation("Mountain View, CA"));
        assertEquals("Scotland, SF", TwitterSearch.toStandardLocation("Scotland / SF"));
        assertEquals("A, B  C", TwitterSearch.toStandardLocation("A,B,  C"));
        assertEquals(null, TwitterSearch.toStandardLocation(""));
    }
}
