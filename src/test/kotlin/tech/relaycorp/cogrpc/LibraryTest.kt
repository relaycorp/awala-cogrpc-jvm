package tech.relaycorp.cogrpc

import kotlin.test.Test
import kotlin.test.assertTrue

class LibraryTest {
    @Test fun testSomeLibraryMethod() {
        val classUnderTest = Library()
        assertTrue(classUnderTest.someLibraryMethod(), "someLibraryMethod should return 'true'")
    }
}
