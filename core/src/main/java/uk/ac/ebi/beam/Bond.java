/*
 * Copyright (c) 2013, European Bioinformatics Institute (EMBL-EBI)
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * The views and conclusions contained in the software and documentation are those
 * of the authors and should not be interpreted as representing official policies,
 * either expressed or implied, of the FreeBSD Project.
 */

package uk.ac.ebi.beam;

/**
 * Enumeration of valid {@link Edge} labels. The connections include all the
 * valid undirected and directed bond types and {@link #DOT}. Opposed to the
 * other types, {@link #DOT} indicates that two atoms are not connected. <p/>
 *
 * <table style="font-family: Courier, monospace;"> <tr><th>{@link
 * Bond}</th><th>{@link #token()}</th><th>{@link #electrons()}</th><th>{@link
 * #inverse()}</th></tr> <tr><td>{@link #DOT}</td><td>.</td><td>0</td><td></td></tr>
 * <tr><td>{@link #IMPLICIT}</td><td></td><td>undefined (2 or
 * 3)</td><td></td></tr> <tr><td>{@link #SINGLE}</td><td>-</td><td>2</td><td></td></tr>
 * <tr><td>{@link #AROMATIC}</td><td>:</td><td>3</td><td></td></tr>
 * <tr><td>{@link #DOUBLE}</td><td>=</td><td>4</td><td></td></tr> <tr><td>{@link
 * #TRIPLE}</td><td>#</td><td>6</td><td></td></tr> <tr><td>{@link
 * #QUADRUPLE}</td><td>$</td><td>8</td><td></td></tr> <tr><td>{@link
 * #UP}</td><td>/</td><td>2</td><td>{@link #DOWN}</td></tr> <tr><td>{@link
 * #DOWN}</td><td>\</td><td>2</td><td>{@link #UP}</td></tr> </table>
 *
 * @author John May
 * @see <a href="http://www.opensmiles.org/opensmiles.html#bonds">Bonds,
 *      OpenSMILES Specification</a>
 */
public enum Bond {

    /** Atoms are not bonded. */
    DOT(".", 0),

    /** Atoms are bonded by either a single or aromatic bond. */
    IMPLICIT("", 2) {
        @Override public int electrons() {
            throw new IllegalArgumentException("unknown number of electrons in implied bond");
        }
    },

    /** Atoms are bonded by a single pair of electrons. */
    SINGLE("-", 2),

    /** Atoms are bonded by two pairs of electrons. */
    DOUBLE("=", 4),

    /** Atoms are bonded by three pairs of electrons. */
    TRIPLE("#", 6),

    /** Atoms are bonded by four pairs of electrons. */
    QUADRUPLE("$", 8),

    /** Atoms are bonded by a delocalized bond of an aromatic system. */
    AROMATIC(":", 3),

    /**
     * Directional, single or aromatic bond (currently always single). The bond
     * is relative to each endpoint such that the second endpoint is
     * <i>above</i> the first or the first end point is <i>below</i> the
     * second.
     */
    UP("/", 2) {
        @Override public Bond inverse() {
            return DOWN;
        }

        @Override boolean directional() {
            return true;
        }
    },

    /**
     * Directional, single or aromatic bond (currently always single). The bond
     * is relative to each endpoint such that the second endpoint is
     * <i>below</i> the first or the first end point is <i>above</i> the
     * second.
     */
    DOWN("\\", 2) {
        @Override public Bond inverse() {
            return UP;
        }

        @Override boolean directional() {
            return true;
        }
    };

    /** The token for the bond in the SMILES grammar. */
    private final String token;

    /** The total number of electrons shared, i.e. not the number of pairs. */
    private final int electrons;

    private Bond(String token, int electrons) {
        this.token = token;
        this.electrons = electrons;
    }

    /**
     * The token of the bond in the SMILES grammar.
     *
     * @return bond token
     */
    public final String token() {
        return token;
    }

    /**
     * The total number electrons (not pairs) shared between atoms. If the label
     * is implicit the value is undefined and invoking the method throws a
     * runtime exception. When the number of electrons is required all implicit
     * labels should be converted to either single or aromatic.
     *
     * @return number of electrons
     * @throws IllegalArgumentException the bond is implicit (single or
     *                                  aromatic) and as such the number of
     *                                  electrons is unknown.
     */
    @Deprecated
    public int electrons() {
        return electrons;
    }

    public int electrons(Atom u, Atom v) {
        return u.aromatic() && v.aromatic() ? 3 : electrons;
    }

    /**
     * Access the inverse of a directional bond ({@link #UP}, {@link #DOWN}). If
     * a bond is non-directional the same bond is returned.
     *
     * @return inverse of the bond
     */
    public Bond inverse() {
        return this;
    }

    /**
     * Create an edge between the vertices {@code u} and {@code v} with this
     * label.
     *
     * <blockquote><pre>
     * Edge e = Bond.IMPLICIT.edge(2, 3);
     * </pre></blockquote>
     *
     * @param u an end point of the edge
     * @param v the other endpoint of the edge
     * @return a new edge labeled with this value
     * @see Edge
     */
    public Edge edge(int u, int v) {
        return new Edge(u, v, this);
    }

    boolean directional() {
        return false;
    }

    /** @inheritDoc */
    public final String toString() {
        return token;
    }
}
