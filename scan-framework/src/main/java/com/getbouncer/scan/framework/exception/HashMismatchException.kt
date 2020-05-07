package com.getbouncer.scan.framework.exception

import java.lang.Exception

class HashMismatchException(val algorithm: String, val expected: String, val actual: String?) :
    Exception("Invalid hash result for algorithm '$algorithm'. Expected '$expected' but got '$actual'") {

    override fun toString(): String {
        return "HashMismatchException(algorithm='$algorithm', expected='$expected', actual='$actual')"
    }
}
