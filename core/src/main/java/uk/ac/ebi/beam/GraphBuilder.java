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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static uk.ac.ebi.beam.Configuration.DoubleBond.TOGETHER;

/**
 * Provides the ability to incrementally build up a chemical graph from atoms
 * and their connections.
 *
 * <blockquote><pre>
 * Graph g = GraphBuilder.create(3)
 *                               .add(Carbon, 3)
 *                               .add(AtomBuilder.aliphatic(Carbon)
 *                                               .hydrogens(2)
 *                                               .build())
 *                               .add(Oxygen, 1)
 *                               .add(0, 1)
 *                               .add(1, 2)
 *                               .add(2, 3)
 *                               .build();
 * </pre></blockquote>
 *
 * @author John May
 */
public final class GraphBuilder {

    /** Current we just use the non-public methods of the actual graph object. */
    private final Graph g;

    private final List<GeometricBuilder> builders = new ArrayList<GeometricBuilder>(2);
    
    private int[] valence;

    /**
     * Internal constructor.
     *
     * @param nAtoms expected number of atoms
     */
    private GraphBuilder(int nAtoms) {
        this.g = new Graph(nAtoms);
        this.valence = new int[nAtoms];
    }

    public static GraphBuilder create(int n) {
        return new GraphBuilder(n);
    }

    /**
     * Add an aliphatic element with the specified number of carbons.
     *
     * @param e      element
     * @param hCount number of hydrogens
     * @return graph builder for adding more atoms/connections
     */
    public GraphBuilder add(Element e, int hCount) {
        return add(AtomBuilder.aliphatic(e)
                              .hydrogens(hCount)
                              .build());
    }

    /**
     * Add an atom to the graph.
     *
     * @param a the atom to add
     * @return graph builder for adding more atoms/connections
     */
    public GraphBuilder add(Atom a) {
        if (g.order() >= valence.length)
            valence = Arrays.copyOf(valence, valence.length * 2);
        g.addAtom(a);
        return this;
    }

    /**
     * Add an edge to the graph.
     *
     * @param e the edge to add
     * @return graph builder for adding more atoms/connections
     */
    public GraphBuilder add(Edge e) {
        Bond b = e.bond();
        int u = e.either();
        int v = e.other(u);
        if (b == Bond.SINGLE && (!g.atom(u).aromatic() || !g.atom(v).aromatic()))
            e.bond(Bond.IMPLICIT);
        else if (b == Bond.AROMATIC && g.atom(u).aromatic() && g.atom(v).aromatic())
            e.bond(Bond.IMPLICIT);
        g.addEdge(e);
        valence[u] += b.order();
        valence[v] += b.order();
        return this;
    }

    /**
     * Connect the vertices u and v with an {@link Bond#IMPLICIT} bond label.
     *
     * @param u a vertex
     * @param v another vertex
     * @return graph builder for adding more atoms/connections
     */
    public GraphBuilder add(int u, int v) {
        add(u, v, Bond.IMPLICIT);
        return this;
    }

    /**
     * Connect the vertices u and v with the specified bond label.
     *
     * @param u a vertex
     * @param v another vertex
     * @return graph builder for adding more atoms/connections
     */
    public GraphBuilder add(int u, int v, Bond b) {
        add(b.edge(u, v));
        return this;
    }

    /**
     * Connect the vertices u and v with a single bond.
     *
     * @param u a vertex
     * @param v another vertex
     * @return graph builder for adding more atoms/connections
     */
    public GraphBuilder singleBond(int u, int v) {
        if (g.atom(u).aromatic() && g.atom(v).aromatic())
            return add(u, v, Bond.SINGLE);
        return add(u, v, Bond.IMPLICIT);
    }

    /**
     * Connect the vertices u and v with an aromatic bond.
     *
     * @param u a vertex
     * @param v another vertex
     * @return graph builder for adding more atoms/connections
     */
    public GraphBuilder aromaticBond(int u, int v) {
        if (g.atom(u).aromatic() && g.atom(v).aromatic())
            return add(u, v, Bond.IMPLICIT);
        return add(u, v, Bond.AROMATIC);
    }

    /**
     * Connect the vertices u and v with a double bond.
     *
     * @param u a vertex
     * @param v another vertex
     * @return graph builder for adding more atoms/connections
     */
    public GraphBuilder doubleBond(int u, int v) {
        return add(u, v, Bond.DOUBLE);
    }

    /**
     * Start building a tetrahedral configuration.
     *
     * @param u the central atom
     * @return a {@link TetrahedralBuilder} to create the stereo-configuration
     *         from
     */
    public TetrahedralBuilder tetrahedral(int u) {
        return new TetrahedralBuilder(this, u);
    }

    /** Start building the geometric configuration of the double bond 'u' / 'v'. */
    public GeometricBuilder geometric(int u, int v) {
        return new GeometricBuilder(this, u, v);
    }

    /**
     * (internal) Add a topology to the chemical graph. The topologies should be
     * created using one of the configuration builders (e.g. {@link
     * TetrahedralBuilder}).
     *
     * @param t the topology to add
     */
    void topology(int u, Topology t) {
        g.addTopology(t);
    }

    private void assignDirectionalLabels() {
        
        // store the vertices which are adjacent to pi bonds with a config
        BitSet adjToDb = new BitSet();
       
        for (GeometricBuilder builder : builders) {
            // unspecified only used for getting not setting configuration
            if (builder.c == Configuration.DoubleBond.UNSPECIFIED)
                continue;
            checkGeometricBuilder(builder); // check required vertices are adjacent

            int u = builder.u, v = builder.v, x = builder.x, y = builder.y;
           
            fix(g, u, v, adjToDb);
            fix(g, v, u, adjToDb);
            
            Bond first  = firstDirectionalLabel(u, x, adjToDb);
            Bond second = builder.c == TOGETHER ? first
                                                : first.inverse();

            // check if the second label would cause a conflict
            if (checkDirectionalAssignment(second, v, y, adjToDb)) {
                // okay to assign the labels as they are
                g.replace(g.edge(u, x), new Edge(u, x, first));
                g.replace(g.edge(v, y), new Edge(v, y, second));
            }
            // there will be a conflict - check if we invert the first one...
            else if (checkDirectionalAssignment(first.inverse(), u, x, adjToDb)) {
                g.replace(g.edge(u, x), new Edge(u, x, (first = first.inverse())));
                g.replace(g.edge(v, y), new Edge(v, y, (second = second.inverse())));
            } else {                                   
                BitSet visited = new BitSet();
                visited.set(v);
                invertExistingDirectionalLabels(adjToDb, visited, v, u);
                if (!checkDirectionalAssignment(first, u, x, adjToDb) ||
                        !checkDirectionalAssignment(second, v, y, adjToDb))
                    throw new IllegalArgumentException("cannot assign geometric configuration");
                g.replace(g.edge(u, x), new Edge(u, x, first));
                g.replace(g.edge(v, y), new Edge(v, y, second));
            }

            // propagate bond directions to other adjacent bonds
            for (Edge e : g.edges(u))
                if (e.bond() != Bond.DOUBLE && !e.bond().directional()) {
                    e.bond(e.either() == u ? first.inverse() : first);
                }
            for (Edge e : g.edges(v))
                if (e.bond() != Bond.DOUBLE && !e.bond().directional()) {
                    e.bond(e.either() == v ? second.inverse() : second);
                }
        

            adjToDb.set(u);
            adjToDb.set(v);
                
        }
        builders.clear();
    }

    private void fix(Graph g, int u, int p, BitSet adjToDb) {
        Bond other = null;
        for (Edge e : g.edges(u)) {
            Bond bond = e.bond(u);
            if (bond.directional()) {
                if (other != null && other == bond) {
                    BitSet visited = new BitSet();
                    visited.set(p);
                    visited.set(e.other(u));
                    invertExistingDirectionalLabels(adjToDb, visited, u, p);
                }
                other = bond;
            }
        }
    }
    
    private void invertExistingDirectionalLabels(BitSet adjToDb,
                                                 BitSet visited,
                                                 int u,
                                                 int p) {
        visited.set(u);
        for (Edge e : g.edges(u)) {
            int v = e.other(u);
            if (!visited.get(v) && p != v) {
                g.replace(e, e.inverse());
                if (adjToDb.get(v))
                    invertExistingDirectionalLabels(adjToDb, visited, v, u);
            }
        }
    }

    private Bond firstDirectionalLabel(int u, int x, BitSet adjToDb) {

        Edge e = g.edge(u, x);
        Bond b = e.bond(u);
        
        // the edge is next to another double bond configuration, we
        // need to consider its assignment
        if (adjToDb.get(x) && g.degree(x) > 2) {
            for (Edge f : g.edges(x)) {
                if (f.other(x) != u && f.bond() != Bond.DOUBLE && f.bond().directional())
                    return f.bond(x);    
            }
        } 
        // consider other labels on this double-bond
        if (g.degree(u) > 2) {
            for (Edge f : g.edges(u)) {
                if (f.other(u) != x && f.bond() != Bond.DOUBLE && f.bond().directional())
                    return f.bond(u).inverse();
            }                                                     
        }
        return b.directional() ? b : Bond.DOWN;
    }

    private boolean checkDirectionalAssignment(Bond b, int u, int v, BitSet adjToDb) {
        
        for (Edge e : g.edges(u)) {
            int x = e.other(u);
            Bond existing = e.bond(u);
            if (existing.directional()) {
                // if there is already a directional label on a different edge
                // and they are equal this produces a conflict
                if (x != v) {
                    if (existing == b) {
                        return false;
                    }
                } else {
                    if (existing != b) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    // safety checks
    private void checkGeometricBuilder(GeometricBuilder builder) {
        if (!g.adjacent(builder.u, builder.x)
                || !g.adjacent(builder.u, builder.v)
                || !g.adjacent(builder.v, builder.y))
            throw new IllegalArgumentException("cannot assign directional labels, vertices were not adjacent" +
                                                       "where not adjacent - expected topology of" +
                                                       " 'x-u=v-y' where x=" + builder.x
                                                       + " u=" + builder.u
                                                       + " v=" + builder.v
                                                       + " y=" + builder.y);
        Edge db = g.edge(builder.u, builder.v);
        if (db.bond() != Bond.DOUBLE)
            throw new IllegalArgumentException("cannot assign double bond configuration to non-double bond");
    }
    
    private void suppress() {
        for (int v = 0; v < g.order(); v++) {
            if (g.topologyOf(v).type() == Configuration.Type.None) {
                Atom atom = g.atom(v);
                if (suppressible(atom, valence[v])) {
                    g.setAtom(v, toSubset(atom));    
                }
            }
        }
    }
    
    private Atom toSubset(Atom a) {
        if (a.aromatic())
            return AtomImpl.AromaticSubset.ofElement(a.element());
        else
            return AtomImpl.AliphaticSubset.ofElement(a.element());
    }

    private boolean suppressible(Atom a, int v) {
        if (!a.subset()
                && a.element().organic()
                && a.isotope() < 0
                && a.charge() == 0
                && a.atomClass() == 0) {
            int h = a.hydrogens();
            if (a.aromatic()) 
                return h == a.element().aromaticImplicitHydrogens(1 + v);
            else
                return h == a.element().implicitHydrogens(v);
        }
        return false;
    }
    
    /**
     * Finalise and build the chemical graph.
     *
     * @return chemical graph instance
     */
    public Graph build() {
        suppress(); 
        assignDirectionalLabels();
        return g;
    }

    /** @author John May */
    public static final class TetrahedralBuilder {

        /**
         * Reference to the graph builder we came from - allows us to add the
         * topology once the configuration as been built.
         */
        final GraphBuilder gb;

        /** Central vertex. */
        final int u;

        /** The vertex we are looking from. */
        int v;

        /** The other neighbors */
        int[] vs;

        /** The configuration of the other neighbors */
        Configuration config;

        /**
         * (internal) - constructor for starting to configure a tetrahedral
         * centre.
         *
         * @param gb the graph builder (where we came from)
         * @param u  the vertex to
         */
        private TetrahedralBuilder(GraphBuilder gb,
                                   int u) {
            this.gb = gb;
            this.u = u;
        }

        /**
         * Indicate from which vertex the tetrahedral is being 'looked-at'.
         *
         * @param v the vertex from which we are looking from.
         * @return tetrahedral builder for further configuration
         */
        public TetrahedralBuilder lookingFrom(int v) {
            this.v = v;
            return this;
        }

        /**
         * Indicate the other neighbors of tetrahedral (excluding the vertex we
         * are looking from). There should be exactly 3 neighbors.
         *
         * @param vs the neighbors
         * @return tetrahedral builder for further configuration
         * @throws IllegalArgumentException when there was not exactly 3
         *                                  neighbors
         */
        public TetrahedralBuilder neighbors(int[] vs) {
            if (vs.length != 3)
                throw new IllegalArgumentException("3 vertex required for tetrahedral centre");
            this.vs = vs;
            return this;
        }

        /**
         * Indicate the other neighbors of tetrahedral (excluding the vertex we
         * are looking from).
         *
         * @param u a neighbor
         * @param v another neighbor
         * @param w another neighbor
         * @return tetrahedral builder for further configuration
         */
        public TetrahedralBuilder neighbors(int u, int v, int w) {
            return neighbors(new int[]{u, v, w});
        }

        /**
         * Convenience method to specify the parity as odd (-1) for
         * anti-clockwise or even (+1) for clockwise. The parity is translated
         * in to 'TH1' and 'TH2' stereo specification.
         *
         * @param p parity value
         * @return tetrahedral builder for further configuration
         */
        public TetrahedralBuilder parity(int p) {
            if (p < 0)
                return winding(Configuration.TH1);
            if (p > 0)
                return winding(Configuration.TH2);
            throw new IllegalArgumentException("parity must be < 0 or > 0");
        }

        /**
         * Specify the winding of the {@link #neighbors(int, int, int)}.
         *
         * @param c configuration {@link Configuration#TH1},{@link
         *          Configuration#TH2}, {@link Configuration#ANTI_CLOCKWISE} or
         *          {@link Configuration#CLOCKWISE}
         * @return tetrahedral builder for further configuration
         */
        public TetrahedralBuilder winding(Configuration c) {
            this.config = c;
            return this;
        }

        /**
         * Finish configuring the tetrahedral centre and add it to the graph.
         *
         * @return the graph-builder to add more atoms/bonds or stereo elements
         * @throws IllegalArgumentException configuration was missing
         */
        public GraphBuilder build() {
            if (config == null)
                throw new IllegalArgumentException("no configuration defined");
            if (vs == null)
                throw new IllegalArgumentException("no neighbors defined");
            Topology t = Topology.tetrahedral(u,
                                              new int[]{
                                                      v,
                                                      vs[0], vs[1], vs[2]
                                              },
                                              config);
            gb.topology(u, t);
            return gb;
        }
    }

    /** Fluent assembly of a double-bond configuration. */
    public static final class GeometricBuilder {
        /**
         * Reference to the graph builder we came from - allows us to add the
         * double bond once the configuration as been built.
         */
        final GraphBuilder gb;
        final int          u, v;
        int x, y;
        Configuration.DoubleBond c;

        public GeometricBuilder(GraphBuilder gb, int u, int v) {
            this.gb = gb;
            this.u = u;
            this.v = v;
        }

        public GraphBuilder together(int x, int y) {
            return configure(x, y, TOGETHER);
        }

        public GraphBuilder opposite(int x, int y) {
            return configure(x, y, Configuration.DoubleBond.OPPOSITE);
        }

        public GraphBuilder configure(int x, int y, Configuration.DoubleBond c) {
            this.x = x;
            this.y = y;
            this.c = c;
            gb.builders.add(this);
            return gb;
        }

        @Override public String toString() {
            return x + "/" + u + "=" + v + (c == TOGETHER ? "\\" : "/") + y;
        }
    }
}
