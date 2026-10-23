package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class PersonalHomeTest {

    @Test
    public void placesParseNameAndCoordinates() {
        List<PersonalHome.Place> places = PersonalHome.places(Arrays.asList(
                "Doha=25.2854,51.5310", " Chennai = 13.0827 , 80.2707 "));
        assertEquals(2, places.size());
        assertEquals("Doha", places.get(0).name);
        assertEquals(25.2854, places.get(0).lat, 1e-9);
        assertEquals(51.5310, places.get(0).lon, 1e-9);
        assertEquals("Chennai", places.get(1).name);
        assertEquals(80.2707, places.get(1).lon, 1e-9);
    }

    @Test
    public void badPlacesAreSkipped() {
        List<PersonalHome.Place> places = PersonalHome.places(Arrays.asList(
                "NoCoordinates", "=1,2", "Nowhere=abc,def", "Off=91,0", "Edge=-90,180"));
        assertEquals(1, places.size());
        assertEquals("Edge", places.get(0).name);
    }

    @Test
    public void taglinesGroupByPackageInOrder() {
        Map<String, List<String>> t = PersonalHome.taglines(Arrays.asList(
                "com.netflix.ninja=One", "in.startv.hotstar=Two", "com.netflix.ninja=Three", "broken", "x=", "=y",
                "com.sonyliv=Risk hai toh ishq hai = yes"));
        assertEquals(Arrays.asList("One", "Three"), t.get("com.netflix.ninja"));
        assertEquals(Collections.singletonList("Two"), t.get("in.startv.hotstar"));
        // Only the first '=' splits, so a line can contain one.
        assertEquals(Collections.singletonList("Risk hai toh ishq hai = yes"), t.get("com.sonyliv"));
        assertEquals(3, t.size());
    }

    @Test
    public void taglinesTakeTurnsPerVisit() {
        List<String> lines = Arrays.asList("a", "b", "c");
        assertEquals("a", PersonalHome.pick(lines, 0));
        assertEquals("b", PersonalHome.pick(lines, 1));
        assertEquals("a", PersonalHome.pick(lines, 3));
        assertEquals("c", PersonalHome.pick(lines, -1));
        assertNull(PersonalHome.pick(Collections.emptyList(), 2));
        assertNull(PersonalHome.pick(null, 2));
    }

    @Test
    public void weatherAsksForEveryPlaceInOneRequest() {
        String url = WeatherLine.url(PersonalHome.places(Arrays.asList("A=25.2854,51.531", "B=12.9165,79.1325")));
        assertTrue(url, url.startsWith("https://api.open-meteo.com/v1/forecast?"));
        assertTrue(url, url.contains("latitude=25.2854,12.9165"));
        assertTrue(url, url.contains("longitude=51.5310,79.1325"));
        assertTrue(url, url.contains("current=temperature_2m,weather_code,is_day"));
    }

    @Test
    public void weatherCodesMapToIcons() {
        assertEquals(R.drawable.ic_wx_sun, WeatherLine.icon(0, true));
        assertEquals(R.drawable.ic_wx_moon, WeatherLine.icon(0, false));
        assertEquals(R.drawable.ic_wx_partly, WeatherLine.icon(2, true));
        assertEquals(R.drawable.ic_wx_cloud, WeatherLine.icon(3, true));
        assertEquals(R.drawable.ic_wx_fog, WeatherLine.icon(45, true));
        assertEquals(R.drawable.ic_wx_rain, WeatherLine.icon(61, true));
        assertEquals(R.drawable.ic_wx_rain, WeatherLine.icon(81, false));
        assertEquals(R.drawable.ic_wx_snow, WeatherLine.icon(73, true));
        assertEquals(R.drawable.ic_wx_snow, WeatherLine.icon(85, true));
        assertEquals(R.drawable.ic_wx_storm, WeatherLine.icon(95, true));
    }

    @Test
    public void lettermarkIsAShortWordOrAnInitial() {
        assertEquals("Prime", LogoArt.mark("Prime Video"));
        assertEquals("J", LogoArt.mark("JioHotstar"));
        assertEquals("S", LogoArt.mark("SonyLIV"));
        assertEquals("?", LogoArt.mark("  "));
    }

    @Test
    public void aLightGlyphOnASolidPlateKeepsOnlyTheGlyph() {
        int plate = 0xFF4A4A50, glyph = 0xFFE0E0E0;
        int[] px = new int[100];
        for (int i = 0; i < px.length; i++) px[i] = i < 20 ? glyph : plate;
        assertTrue(LogoArt.keepGlyph(px));
        assertEquals(0xFF, px[0] >>> 24);    // glyph stays opaque
        assertEquals(0, px[50] >>> 24);      // plate turns transparent
    }

    @Test
    public void aDarkGlyphOnALightPlateWorksToo() {
        int[] px = new int[100];
        for (int i = 0; i < px.length; i++) px[i] = i < 20 ? 0xFF101010 : 0xFFF0F0F0;
        assertTrue(LogoArt.keepGlyph(px));
        assertEquals(0xFF, px[0] >>> 24);
        assertEquals(0, px[99] >>> 24);
    }

    @Test
    public void aPlainSilhouetteIsLeftAlone() {
        int[] px = new int[100];
        for (int i = 0; i < px.length; i++) px[i] = i % 7 == 0 ? 0x00000000 : 0xFF000000;
        int[] before = px.clone();
        assertTrue(!LogoArt.keepGlyph(px));
        assertTrue(java.util.Arrays.equals(before, px));
    }

    @Test
    public void eachWeatherGetsItsEffect() {
        assertEquals(WeatherFx.SNOW, WeatherFx.kindFor(73, true));
        assertEquals(WeatherFx.SNOW, WeatherFx.kindFor(86, false));
        assertEquals(WeatherFx.RAIN, WeatherFx.kindFor(61, true));
        assertEquals(WeatherFx.RAIN, WeatherFx.kindFor(53, false));
        assertEquals(WeatherFx.STORM, WeatherFx.kindFor(95, true));
        assertEquals(WeatherFx.NONE, WeatherFx.kindFor(45, true));    // fog: no veil over the screen
        assertEquals(WeatherFx.NONE, WeatherFx.kindFor(3, false));    // overcast
        assertEquals(WeatherFx.NONE, WeatherFx.kindFor(2, true));     // partly cloudy
        assertEquals(WeatherFx.SUN, WeatherFx.kindFor(1, true));
        assertEquals(WeatherFx.STARS, WeatherFx.kindFor(0, false));
        assertEquals(WeatherFx.SUN, WeatherFx.kindFor(0, true));
    }

    @Test
    public void otherCitiesShowTheirLocalTime() {
        long noonUtc = 1_761_220_800_000L;   // 2025-10-23 12:00 UTC
        assertEquals("5:30 PM", WeatherLine.localTime(noonUtc, 5 * 3600 + 1800, false));   // India
        assertEquals("08:00", WeatherLine.localTime(noonUtc, -4 * 3600, true));             // Toronto, summer time
        assertEquals("3:00 PM", WeatherLine.localTime(noonUtc, 3 * 3600, false));           // Doha
    }

    @Test
    public void wallpaperSetsAreNamedFromTheirFolders() {
        assertEquals("Scenes", PersonalHome.setName("1-scenes"));
        assertEquals("Aurora", PersonalHome.setName("2_aurora"));
        assertEquals("My trip", PersonalHome.setName("my_trip"));
        assertEquals("42", PersonalHome.setName("42"));
    }

    @Test
    public void aBreezyDryDayIsWindy() {
        assertTrue(WeatherLine.windy(0, 30));
        assertTrue(WeatherLine.windy(3, 55));
        assertTrue(!WeatherLine.windy(1, 29));
        assertTrue(!WeatherLine.windy(61, 50));   // rain stays rain however windy
        assertEquals(R.drawable.ic_wx_wind, new WeatherLine.Reading("A", 20, 0, true, 0, true, 40).icon);
        assertEquals(R.drawable.ic_wx_sun, new WeatherLine.Reading("A", 20, 0, true, 0, true, 10).icon);
        assertEquals(WeatherFx.WIND, WeatherFx.kindNamed("wind"));
    }

    @Test
    public void cormorantIsDrawnLarger() {
        assertEquals(1.18f, PersonalHome.fontScale("cormorantgaramond.ttf"), 1e-6);
        assertEquals(1f, PersonalHome.fontScale("raleway.ttf"), 1e-6);
        assertEquals(1f, PersonalHome.fontScale(null), 1e-6);
    }

    @Test
    public void birthdaysAreFoundByDayAndMonth() {
        List<String> list = Arrays.asList("Anya=14-03", "Ravi=02-11-1985", "Amma=14/3", "bad", "Nope=xx-03");
        assertEquals(Arrays.asList("Anya", "Amma"), PersonalHome.celebrating(list, 14, 3));
        assertEquals(Collections.singletonList("Ravi"), PersonalHome.celebrating(list, 2, 11));
        assertTrue(PersonalHome.celebrating(list, 3, 14).isEmpty());   // day first, not month first
    }

    @Test
    public void birthdayGreetingNamesEveryone() {
        assertEquals("Happy birthday, Anya!", PersonalHome.greeting(Collections.singletonList("Anya")));
        assertEquals("Happy birthday, Anya and Ravi!", PersonalHome.greeting(Arrays.asList("Anya", "Ravi")));
        assertEquals("Happy birthday, Anya, Ravi and Amma!", PersonalHome.greeting(Arrays.asList("Anya", "Ravi", "Amma")));
    }

    @Test
    public void previewNamesAnEffect() {
        assertEquals(WeatherFx.SNOW, WeatherFx.kindNamed("Snow"));
        assertEquals(WeatherFx.STORM, WeatherFx.kindNamed("storm"));
        assertEquals(-1, WeatherFx.kindNamed(""));
        assertEquals(-1, WeatherFx.kindNamed("hail"));
    }

    @Test
    public void logosTakeTurnsPerVisit() {
        List<String> files = Arrays.asList("com.sonyliv.png", "in.startv.hotstar.2.png", "in.startv.hotstar.png",
                "in.startv.hotstar.notes.png", "in.startv.hotstarx.png", "other.png");
        assertEquals("in.startv.hotstar.2.png", LogoArt.pickName(files, "in.startv.hotstar", 0));
        assertEquals("in.startv.hotstar.png", LogoArt.pickName(files, "in.startv.hotstar", 1));
        assertEquals("in.startv.hotstar.2.png", LogoArt.pickName(files, "in.startv.hotstar", 2));
        assertEquals("com.sonyliv.png", LogoArt.pickName(files, "com.sonyliv", 5));
        assertNull(LogoArt.pickName(files, "com.netflix.ninja", 0));
    }
}
