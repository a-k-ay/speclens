package dev.akshita.speclens.ai;

/** pgvector accepts vectors as text like '[0.1,0.2,0.3]', which we cast with ::vector in SQL. */
public final class Vectors {

	private Vectors() {
	}

	public static String toPgVector(float[] vector) {
		StringBuilder sb = new StringBuilder(vector.length * 10).append('[');
		for (int i = 0; i < vector.length; i++) {
			if (i > 0) {
				sb.append(',');
			}
			sb.append(vector[i]);
		}
		return sb.append(']').toString();
	}

}
