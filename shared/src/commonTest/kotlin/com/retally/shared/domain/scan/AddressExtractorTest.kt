/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.domain.scan

import kotlin.test.Test
import kotlin.test.assertEquals

class AddressExtractorTest {

    @Test
    fun cityStateZipTwoLines() {
        assertEquals(
            ParsedAddress("123 Main St", "Edison", "NJ", "08817"),
            AddressExtractor.extract("123 Main St\nEdison, NJ 08817"),
        )
    }

    @Test
    fun singleLineCommas() {
        assertEquals(
            ParsedAddress("123 Main St", "Edison", "NJ", "08817"),
            AddressExtractor.extract("123 Main St, Edison, NJ 08817"),
        )
    }

    @Test
    fun noCommaUppercaseCity() {
        assertEquals(
            ParsedAddress("123 Main St", "EDISON", "NJ", "08817"),
            AddressExtractor.extract("123 Main St\nEDISON NJ 08817"),
        )
    }

    @Test
    fun noZip() {
        assertEquals(
            ParsedAddress("123 Main St", "Edison", "NJ", ""),
            AddressExtractor.extract("123 Main St\nEdison, NJ"),
        )
    }

    @Test
    fun zipNoState() {
        assertEquals(
            ParsedAddress("123 Main St", "Edison", "", "08817"),
            AddressExtractor.extract("123 Main St\nEdison 08817"),
        )
    }

    @Test
    fun cityAndZipOnly() {
        assertEquals(
            ParsedAddress("", "Edison", "", "08817"),
            AddressExtractor.extract("Edison 08817"),
        )
    }

    @Test
    fun fullStateName() {
        assertEquals(
            ParsedAddress("123 Main St", "Edison", "NJ", "08817"),
            AddressExtractor.extract("123 Main St\nEdison, New Jersey 08817"),
        )
    }

    @Test
    fun westVirginiaBeatsVirginia() {
        assertEquals(
            ParsedAddress("123 Main St", "Charleston", "WV", "25301"),
            AddressExtractor.extract("123 Main St\nCharleston, West Virginia 25301"),
        )
    }

    @Test
    fun multiLineStreetAndZipPlus4() {
        assertEquals(
            ParsedAddress("123 Main St\nApt 4", "Edison", "NJ", "08817-1234"),
            AddressExtractor.extract("123 Main St\nApt 4\nEdison, NJ 08817-1234"),
        )
    }

    @Test
    fun streetOnlyKeepsEverything() {
        assertEquals(
            ParsedAddress("123 Main St", "", "", ""),
            AddressExtractor.extract("123 Main St"),
        )
    }

    @Test
    fun emptyBlock() {
        assertEquals(
            ParsedAddress("", "", "", ""),
            AddressExtractor.extract(""),
        )
    }

    @Test
    fun trailingPhoneLineDropped() {
        assertEquals(
            ParsedAddress("123 Main St", "Edison", "NJ", "08817"),
            AddressExtractor.extract("123 Main St\nEdison, NJ 08817\n781-499-1961"),
        )
    }

    @Test
    fun lowercaseStateUppercased() {
        assertEquals(
            ParsedAddress("123 main st", "edison", "NJ", "08817"),
            AddressExtractor.extract("123 main st\nedison, nj 08817"),
        )
    }

    @Test
    fun weymouthTwoLines() {
        assertEquals(
            ParsedAddress("740 MIDDLE ST", "WEYMOUTH", "MA", "02188"),
            AddressExtractor.extract("740 MIDDLE ST\nWEYMOUTH MA 02188"),
        )
    }

    @Test
    fun singleLineStreetAndCity() {
        assertEquals(
            ParsedAddress("740 MIDDLE ST", "WEYMOUTH", "MA", "02188"),
            AddressExtractor.extract("740 MIDDLE ST WEYMOUTH MA 02188"),
        )
    }

    @Test
    fun singleLinePoBox() {
        assertEquals(
            ParsedAddress("PO BOX 123", "WEYMOUTH", "MA", "02188"),
            AddressExtractor.extract("PO BOX 123 WEYMOUTH MA 02188"),
        )
    }

    @Test
    fun singleLineStreetOnlyStaysStreet() {
        assertEquals(
            ParsedAddress("740 MIDDLE ST", "", "", ""),
            AddressExtractor.extract("740 MIDDLE ST"),
        )
    }

    @Test
    fun weymouthStreetTwoLines() {
        assertEquals(
            ParsedAddress("740 MIDDLE STREET", "WEYMOUTH", "MA", "02188"),
            AddressExtractor.extract("740 MIDDLE STREET\nWEYMOUTH MA 02188"),
        )
    }

    @Test
    fun weymouthStreetWithTrailingStoreNumber() {
        assertEquals(
            ParsedAddress("740 MIDDLE STREET", "WEYMOUTH", "MA", "02188"),
            AddressExtractor.extract("740 MIDDLE STREET\nWEYMOUTH MA 02188\nST# 03200 OP# 003067 TE# 06 TR# 00669"),
        )
    }

    @Test
    fun trailingPhoneWithLettersDropped() {
        assertEquals(
            ParsedAddress("123 Main St", "Edison", "NJ", "08817"),
            AddressExtractor.extract("123 Main St\nEdison, NJ 08817\n781 331-0063 Mgr. ED"),
        )
    }
}
