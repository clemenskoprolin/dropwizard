package benchmarks.edgecases;

/**
 * Edge case: inner-class field shadowing and builder-style this-return chains.
 *
 * Hypothesis: When an inner class declares a field with the same name as the
 * enclosing class, J2K must qualify the enclosing reference with
 * OuterClass.this.fieldName. In Kotlin this becomes this@OuterClass.fieldName.
 * J2K is expected to handle one level correctly but may emit !! or incorrect
 * resolution for deeply nested (inner of inner) access patterns.
 */
public class InnerClassShadowing {

    private String value = "outer-value";
    private int count = 0;

    public class Inner {
        // shadows enclosing field
        private String value = "inner-value";

        public String getOuterValue() {
            // must resolve to InnerClassShadowing.this.value
            return InnerClassShadowing.this.value;
        }

        public String getInnerValue() {
            return value;
        }

        public void incrementOuter() {
            InnerClassShadowing.this.count++;
        }

        public class InnerInner {
            private String value = "inner-inner-value";

            public String getAll() {
                // three levels — J2K may struggle here
                return InnerClassShadowing.this.value
                        + " | " + Inner.this.value
                        + " | " + value;
            }
        }
    }

    // Static nested class — no outer reference, should be straightforward
    public static class StaticNested {
        private String value;

        public StaticNested(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    public Inner createInner() {
        return new Inner();
    }

    public int getCount() {
        return count;
    }
}
