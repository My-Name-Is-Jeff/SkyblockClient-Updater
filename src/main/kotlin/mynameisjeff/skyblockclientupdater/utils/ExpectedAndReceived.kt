package mynameisjeff.skyblockclientupdater.utils
import java.lang.Math;

object ExpectedAndReceived {
    val expected: String? = null // string a
    val received: String? = null // string b

    constructor(expected: String, received: String) {
        this.expected = expected
        this.received = received
    }

    fun needsUpdate(distance: Integer): Boolean {
        val exemptChars = charArrayOf('_', '-', '+', ' ', '.', '!')
        val numbers = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9')
        var expectedChars =  ArrayList<Char?>()
        var receivedChars =  ArrayList<Char?>()

        /*
        for index in range(0, max(len(stra), len(strb))):
            if index < min(len(stra), len(strb)):
                if stra[index] != strb[index]:
                    print(f"Letter Expected: {stra[index]}")
                    print(f"Letter Recieved: {strb[index]}")
                    print()
            else:
                a = "null"
                b = "null"
                if len(stra) > len(strb):
                    a = stra[index]
                else:
                    b = strb[index]

                print(f"Letter Expected: {a}" )
                print(f"Letter Recieved: {b}")
                print()
        */

        for (index in 0..Math.max(expected.length(), received.length())) {
            if (index < Math.min(expected.length(), received.length())) {
                if (!expected[index].equals(received[index])) {
                    //expectedChars = charArrayOf(expectedChars.all, expected[index])
                    //receivedChars = charArrayOf(receivedChars.all, received[index])
                    expectedChars.add(expected[index]))
                    receivedChars.add(received[index]))
                }
            }
            else {
                var e = null
                var r = null
                
                if (expected.length() > received.length()) {
                    e = expected[index]
                }
                else {
                    r = received[index]
                }

                //expectedChars = charArrayOf(expectedChars.all, r)
                //receivedChars = charArrayOf(receivedChars.all, e)
                receivedChars.add(r)
                expectedChars.add(e)
            }
        }


        if (distance == 1 && expected.length() == received.length()) {
            var ech: Char = expectedChars.get(0) // ArrayList<Char>()
            var rch: Char = receivedChars.get(0) // ArrayList<Char>()
            
            if (ech.isDigit() && rch.isDigit()) {
                return false
            }
            else {
                if (ech in numbers && rch in numbers) {
                    return rch.toString().toInt() > ech.toString().toInt()
                }
            }

        }
        return true;
    }
}
