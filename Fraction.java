public class Fraction {

    private final int numerator;
    private final int denominator;

    private Fraction(final int numer_in, final int denom_in){
        if(denom_in == 0){
            numerator = 0;
            denominator = 0;
        } else if(numer_in == 0){
            numerator = 0;
            denominator = 1;
        } else {
            final int numer_abs = Math.abs(numer_in);
            final int denom_abs = Math.abs(denom_in);
            final int sign = ((numer_in >> 31) ^ (denom_in >> 31)) | 1;
            final int gcd = gcd(numer_abs, denom_abs);
            numerator = (numer_abs / gcd) * sign;
            denominator = denom_abs / gcd;
        }
    }

    private static int gcd(int a, int b){
        while(b != 0){
            int temp = b;
            b = a % b;
            a = temp;
        }
        return a;
    }

    public static Fraction fromInt(int n){
        return new Fraction(n, 1);
    }

    public static Fraction fromInverse(int d){
        return new Fraction(1, d);
    }

    public static Fraction fromBoth(int n, int d){
        return new Fraction(n, d);
    }

    @Override
    public String toString(){
        StringBuilder sb = new StringBuilder();
        if(denominator == 0){
            sb.append("Undefined");
        } else {
            sb.append(numerator);
            if(denominator != 1){
                sb.append('/');
                sb.append(denominator);
            }
        }
        return sb.toString();
    }

    public int getNumerator(){
        return numerator;
    }

    public int getDenominator(){
        return denominator;
    }

    public boolean isInteger(){
        return denominator == 1;
    }

    public boolean isUndefined(){
        return denominator == 0;
    }

    public Fraction multiply(Fraction other){
        return new Fraction(numerator * other.numerator, denominator * other.denominator);
    }

    public Fraction divide(Fraction other){
        return new Fraction(numerator * other.denominator, denominator * other.numerator);
    }

    public int roundToInt(){
        final int numer_abs = Math.abs(numerator);
        final int denom_abs = denominator; //This is always positive due to behavior of constructor.
        final int sign = (numerator >> 31) | 1;
        int basis = 0;
        int extra = 0;
        if(numer_abs >= denom_abs){
            basis = numer_abs / denom_abs;
            extra = numer_abs % denom_abs;
        } else {
            extra = numer_abs;
        }
        if((2L * extra) > denom_abs){
            basis++;
        }
        return basis * sign;
    }

}