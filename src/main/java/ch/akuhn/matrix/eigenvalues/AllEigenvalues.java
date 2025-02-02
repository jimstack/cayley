package ch.akuhn.matrix.eigenvalues;

import java.util.Arrays;

import com.github.fommil.netlib.LAPACK;
import org.netlib.util.intW;

import ch.akuhn.matrix.KuhnMatrix;
import ch.akuhn.matrix.Vector;

/**
 * Finds all eigenvalues of a matrix.
 * <p>
 * Computes for an <code>n</code>&times;<code>n</code> real non-symmetric matrix
 * <code>A</code>, the eigenvalues (&lambda;) and, optionally, the left and/or
 * right eigenvectors. The computed eigenvectors are normalized to have
 * Euclidean norm equal to 1 and largest component real.
 * <p>
 *
 * @author Adrian Kuhn
 *
 * @see "http://www.netlib.org/lapack/double/dgeev.f"
 */
public class AllEigenvalues extends Eigenvalues {

    private static final boolean l = true;
    private static final boolean r = false;

    private final LAPACK lapack = LAPACK.getInstance();
    private final KuhnMatrix A;

    /**
     * Construct with the given matrix
     *
     * @param A
     */
    public AllEigenvalues(KuhnMatrix A) {
        super(A.columnCount());
        if (!A.isSquare()) {
            throw new IllegalArgumentException("A is not square");
        }
        this.A = A;
    }

    @Override
    public AllEigenvalues run() {
        double[] wr = new double[n];
        double[] wi = new double[n];
        intW info = new intW(0);
        double[] a = A.asColumnMajorArray();
        double[] vl = new double[l ? n * n : 0];
        double[] vr = new double[r ? n * n : 0];
        double[] work = allocateWorkspace();
        lapack.dgeev(
                jobv(l),
                jobv(r),
                n,
                a, // overwritten on output!
                n,
                wr, // output: real eigenvalues
                wi, // output: imaginary eigenvalues
                vl, // output:: left eigenvectors
                n,
                vr, // output:: right eigenvectors
                n,
                work,
                work.length,
                info);
        if (info.val != 0) {
            throw new RuntimeException("dgeev ERRNO=" + info.val);
        }
        postprocess(wr, vl);
        return this;
    }

    /**
     * <pre>
     * [wr,vl.enum_cons(n)]
     *  .transpose
     *  .sort_by(&:first)
     *  .tranpose
     *  .revert
     * </pre>
     */
    private void postprocess(double[] wr, double[] vl) {
        class Eigen implements Comparable<Eigen> {
            double value0;
            Vector vector0;

            @Override
            public int compareTo(Eigen eigen) {
                return Double.compare(value0, eigen.value0);
            }
        }
        Eigen[] eigen = new Eigen[n];
        for (int i = 0; i < n; i++) {
            eigen[i] = new Eigen();
            eigen[i].value0 = wr[i];
            eigen[i].vector0 = Vector.copy(vl, i * n, n);
        }
        Arrays.sort(eigen);
        value = new double[nev];
        vector = new Vector[nev];
        for (int i = 0; i < nev; i++) {
            value[i] = eigen[n - nev + i].value0;
            vector[i] = eigen[n - nev + i].vector0;
        }
    }

    private String jobv(boolean canHasVectors) {
        return canHasVectors ? "V" : "N";
    }

    /**
     * If LWORK = -1, then a workspace query is assumed; the routine only
     * calculates the optimal size of the WORK array, returns this value as the
     * first entry of the WORK array.
     */
    private double[] allocateWorkspace() {
        @SuppressWarnings("unused")
        int lwork = ((l || r) ? 4 : 3) * n;
        double[] query = new double[1];
        intW info = new intW(0);
        lapack.dgeev(
                jobv(l),
                jobv(r),
                n,
                null,
                n,
                null,
                null,
                null,
                n,
                null,
                n,
                query,
                -1,
                info);
        if (info.val == 0) {
            lwork = (int) query[0];
        }
        return new double[lwork];
    }
}
