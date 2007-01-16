package artofillusion.polymesh;

import java.util.Date;
import java.util.Vector;

import buoy.widget.BFrame;
import artofillusion.math.Vec3;
import artofillusion.object.TriangleMesh;
import artofillusion.object.TriangleMesh.Edge;
import artofillusion.object.TriangleMesh.Vertex;
import no.uib.cipr.matrix.*;
import no.uib.cipr.matrix.sparse.*;

public class MeshUnfolder {
    protected TriangleMesh mesh;

    private double[] angles; // mesh angles, 3 angles for each triangle

    private double[] weights;

    private double[] var; // variables, i.e. angles plus Lagrange

    // multipliers

    private double gradient[];

    private CompRowMatrix j2matrix; // J2 matrix as defined in ABF++

    FlexCompRowMatrix jstar; // J* matrix as defined in ABF++

    FlexCompRowMatrix jstarstar; // J** matrix as defined in ABF++

    FlexCompRowMatrix matrix; // matrix of linear system to solve

    DenseVector bb2vec; // right hand of linear system to solve

    double[] lambdaStar; // diagonal of lambda matrix

    double[] lambdaStarInv; // diagonal of invers lambda matrix

    private int[][] j2nonzero; // per row array of array to non zero column
                                // elements

    private int[][] angleTable; // angle references for each interior vertex

    private int[][] anglePTable; // angle references for each interior

    // vertex, plus one (see ABF++)

    private int[][] angleMTable; // angle references for each interior

    // vertex, minus one (see ABF++)

    private int[] interiorVertices; // interior vertices array

    private double[] constraints; // constraints values
    
    private DenseVector weightsVec; //angle weights	

    private int nint; // number of interior vertices

    private int ntri; // number of triangles

    private int nangles; // number of angles

    private double[] bstar; // ABF++ vector

    private double[] solution; // solution to full linear system

    private int[] invInteriorTable; // inverse table : given an interior
                                        // vertex, yields the index to vertex in
                                        // the mesh
    private TriangleMesh unfoldedMesh;
    
    protected BFrame parentWindow;

    /**
         * Creates a new unfolder instance. This class unfolds triangle meshes.
         * 
         * @param mesh
         *                The mesh to unfold.
         * @param parentWindow
         *                The window to display dialogs on top of.
         */
    public MeshUnfolder(TriangleMesh mesh, BFrame parentWindow) {
	this.mesh = mesh;
	this.parentWindow = parentWindow;
    }

    /**
     * Unfolds the mesh using the ABF++ algorithm
     *
     */
    public void unfold() {
	// dump mesh;
	TriangleMesh.Edge[] edges = mesh.getEdges();
	int nedges = edges.length;
	long solvetime, totaltime;
	long mat3time, bstartime, jstartime, jstarstartime, tmptime, solutiontime;
	long t = 0;
	solvetime = mat3time = bstartime = jstartime = tmptime = jstarstartime = solutiontime = 0;
	BiCGstab bicg = null;
	int ind[];
	int jnd[];
	double[] idata;
	double[] jdata;
	SparseVector JstarVecI;
	SparseVector JstarVecJ;
	SparseVector JstarstarVec;
	double value;
	int iter = 0;

	initialize();
	computeConstraints();
	computeJ2();
	computeGradient();
	double residual = computeResidual();
	System.out.println("Initial residual: " + residual);
	// checkGradient();

	// iteration
	totaltime = new Date().getTime();
	while (residual > 1.0e-6) {
	    // bstar computation
	    t = new Date().getTime();
	    for (int i = 0; i < ntri; i++) {
		bstar[i] = constraints[i];
		for (int j = 0; j < 3; j++) {
		    bstar[i] += (weights[3 * i + j] / 2) * -gradient[3 * i + j];
		}
	    }
	    for (int i = 0; i < nint; i++) {
		bstar[ntri + i] = constraints[ntri + i];
		bstar[ntri + nint + i] = constraints[ntri + nint + i];
		for (int j = 0; j < angleTable[i].length; j++) {
		    bstar[ntri + i] += j2matrix.get(i, angleTable[i][j])
			    * (weights[angleTable[i][j]] / 2)
			    * -gradient[angleTable[i][j]];
		    bstar[ntri + nint + i] += j2matrix.get(nint + i,
			    anglePTable[i][j])
			    * (weights[anglePTable[i][j]] / 2)
			    * -gradient[anglePTable[i][j]];
		    bstar[ntri + nint + i] += j2matrix.get(nint + i,
			    angleMTable[i][j])
			    * (weights[angleMTable[i][j]] / 2)
			    * -gradient[angleMTable[i][j]];
		}
	    }
	    bstartime += new Date().getTime() - t;
	    t = new Date().getTime();
	    // jstar computation
	    if (iter != 0) {
		jstar.zero();
	    }
	    int index;
	    for (int i = 0; i < 2 * nint; i++) {
		for (int j = 0; j < j2nonzero[i].length; j++) {
		    index = j2nonzero[i][j] / 3;
		    jstar.add(i, index, j2matrix.get(i, j2nonzero[i][j])
			    * (weights[j2nonzero[i][j]] / 2));
		}
	    }
	    if (iter!=0) {
		jstar.compact();
	    }
	    jstartime += new Date().getTime() - t;
	    // new jstarstar computation
	    t = new Date().getTime();
	    if (iter != 0) {
		jstarstar.zero();
	    }
	    for (int e = 0; e < nedges; e++) {
		int i = invInteriorTable[edges[e].v1];
		int j = invInteriorTable[edges[e].v2];
		if (i == -1 || j == -1)
		    continue;
		value = jstarstarCompute(i, j);
		jstarstar.set(i, j, value);
		jstarstar.set(j, i, value);
		i += nint;
		value = jstarstarCompute(i, j);
		jstarstar.set(i, j, value);
		jstarstar.set(j, i, value);
		i -= nint;
		j += nint;
		value = jstarstarCompute(i, j);
		jstarstar.set(i, j, value);
		jstarstar.set(j, i, value);
		i += nint;
		value = jstarstarCompute(i, j);
		jstarstar.set(i, j, value);
		jstarstar.set(j, i, value);
	    }
	    for (int e = 0; e < nint; e++) {
		value = jstarstarCompute(e, e);
		jstarstar.set(e, e, value);
		value = jstarstarCompute(e + nint, e);
		jstarstar.set(e + nint, e, value);
		value = jstarstarCompute(e, e + nint);
		jstarstar.set(e, e + nint, value);
		value = jstarstarCompute(e + nint, e + nint);
		jstarstar.set(e + nint, e + nint, value);
	    }
	    if (iter == 0) {
		jstarstar.compact();
	    }
	    jstarstartime += new Date().getTime() - t;
	    t = new Date().getTime();
	    if (iter != 0) {
		matrix.zero();
		bb2vec.zero();
	    	matrix.zero();
	    }
	    for (int e = 0; e < nedges; e++) {
		int i = invInteriorTable[edges[e].v1];
		int j = invInteriorTable[edges[e].v2];
		if (i == -1 || j == -1)
		    continue;
		addToMat(matrix, jstar, lambdaStarInv, i, j);
		addToMat(matrix, jstar, lambdaStarInv, i + nint, j);
		addToMat(matrix, jstar, lambdaStarInv, i, j + nint);
		addToMat(matrix, jstar, lambdaStarInv, i + nint, j + nint);
	    }
	    for (int i = 0; i < nint; i++) {
		addToMat(matrix, jstar, lambdaStarInv, i);
		addToMat(matrix, jstar, lambdaStarInv, i + nint, i);
		addToMat(matrix, jstar, lambdaStarInv, i + nint);
		JstarstarVec = jstarstar.getRow(i);
		jnd = JstarstarVec.getIndex();
		jdata = JstarstarVec.getData();
		for (int j = 0; j < jnd.length; j++) {
		    matrix.add(i, jnd[j], -jdata[j]);
		}
		JstarstarVec = jstarstar.getRow(i + nint);
		jnd = JstarstarVec.getIndex();
		jdata = JstarstarVec.getData();
		for (int j = 0; j < jnd.length; j++) {
		    matrix.add(i + nint, jnd[j], -jdata[j]);
		}
	    }
	    for (int i = 0; i < 2 * nint; i++) {
		JstarVecI = jstar.getRow(i);
		ind = JstarVecI.getIndex();
		idata = JstarVecI.getData();
		bb2vec.set(i, -bstar[i + ntri]);
		for (int k = 0; k < ind.length; k++) {
		    bb2vec.add(i, idata[k] * lambdaStarInv[ind[k]]
			    * bstar[ind[k]]);
		}
	    }
	    if (iter == 0) {
		matrix.compact();
	    }
	    mat3time += new Date().getTime() - t;
	    t = new Date().getTime();
	    DenseVector bb2sol = new DenseVector(2 * nint);
	    if (bicg == null)
		bicg= new BiCGstab(bb2sol);
	    try {
		bicg.solve(matrix, bb2vec, bb2sol);
	    } catch (IterativeSolverNotConvergedException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	    }
	    solvetime += new Date().getTime() - t;
	    t = new Date().getTime();
	    double[] newbb2sol = bb2sol.getData();
	    for (int i = 0; i < 2 * nint; i++) {
		solution[4 * ntri + i] = newbb2sol[i];
	    }
	    for (int i = 0; i < ntri; i++) {
		solution[nangles + i] = lambdaStarInv[i] * bstar[i];
	    }
	    for (int i = 0; i < nangles; i++) {
		solution[i] = 0;
	    }
	    for (int j = 0; j < 2 * nint; j++) {
		JstarVecJ = jstar.getRow(j);
		jnd = JstarVecJ.getIndex();
		jdata = JstarVecJ.getData();
		for (int i = 0; i < jnd.length; i++) {
		    solution[nangles + jnd[i]] -= lambdaStarInv[jnd[i]]
			    * jdata[i] * newbb2sol[j];
		}
	    }
	    for (int i = 0; i < ntri; i++) {
		for (int k = 0; k < 3; k++) {
		    solution[3 * i + k] += solution[i + nangles];
		}
	    }
	    for (int j = ntri; j < ntri + 2 * nint; j++) {
		for (int i = 0; i < j2nonzero[j - ntri].length; i++) {
		    solution[j2nonzero[j - ntri][i]] += j2matrix.get(j - ntri,
			    j2nonzero[j - ntri][i])
			    * solution[j + nangles];
		}
	    }
	    for (int i = 0; i < nangles; i++) {
		solution[i] = (weights[i] / 2) * (-gradient[i] - solution[i]);
	    }
	    solutiontime += t - new Date().getTime();
	    for (int i = 0; i < var.length; i++) {
		var[i] += solution[i];
	    }
	    computeConstraints();
	    computeJ2();
	    computeGradient();
	    residual = computeResidual();
	    System.out.println("residual: " + residual);
	    System.out.println("bstar time :" + (bstartime / 1000.0));
	    System.out.println("jstar time :" + (jstartime / 1000.0));
	    System.out.println("mat3 time :" + (mat3time / 1000.0));
	    System.out.println("mat3 time :" + (mat3time / 1000.0));
	    System.out.println("solve time :" + (solvetime / 1000.0));
	    System.out.println("solution time :" + (solutiontime / 1000.0));
	    ++iter;
	}
	System.out.println("Iterations: " + iter);
	totaltime = new Date().getTime() - totaltime;
	System.out.println("residual: " + residual);
	System.out.println("bstar time :" + (bstartime / 1000.0));
	System.out.println("jstar time :" + (jstartime / 1000.0));
	System.out.println("jstarstar time :" + (jstarstartime / 1000.0));
	System.out.println("mat3 time :" + (mat3time / 1000.0));
	System.out.println("newmat3 time :" + (tmptime / 1000.0));
	System.out.println("solve time :" + (solvetime / 1000.0));
	System.out.println("solution time :" + (solutiontime / 1000.0));
	System.out.println("total time :" + (totaltime / 1000.0));
	unfoldedMesh = (TriangleMesh) mesh.duplicate();
	Edge[] uedges = unfoldedMesh.getEdges();
//	double dmax = -1;
//	for (int i = 0; i < uedges.length; i++) {
//	    if ()
//	}
    }

    private void addToMat(FlexCompRowMatrix mat, FlexCompRowMatrix sparseJstar,
	    double[] lambdaStarInv, int v1, int v2) {

	int ind[];
	int jnd[];
	double[] idata;
	double[] jdata;
	SparseVector JstarVecI;
	SparseVector JstarVecJ;
	JstarVecI = sparseJstar.getRow(v1);
	ind = JstarVecI.getIndex();
	idata = JstarVecI.getData();
	JstarVecJ = sparseJstar.getRow(v2);
	jnd = JstarVecJ.getIndex();
	jdata = JstarVecJ.getData();
	for (int k = 0; k < ind.length; k++) {
	    for (int l = 0; l < jnd.length; l++) {
		if (ind[k] == jnd[l]) {
		    mat
			    .add(v1, v2, idata[k] * lambdaStarInv[ind[k]]
				    * jdata[l]);
		    mat
			    .add(v2, v1, idata[k] * lambdaStarInv[ind[k]]
				    * jdata[l]);
		}
	    }
	}
    }

    private void addToMat(FlexCompRowMatrix mat, FlexCompRowMatrix sparseJstar,
	    double[] lambdaStarInv, int v1) {

	int ind[];
	double[] idata;
	SparseVector JstarVecI;
	JstarVecI = sparseJstar.getRow(v1);
	ind = JstarVecI.getIndex();
	idata = JstarVecI.getData();
	for (int k = 0; k < ind.length; k++) {
	    mat.add(v1, v1, idata[k] * lambdaStarInv[ind[k]] * idata[k]);
	}
    }

    private double jstarstarCompute(int i, int j) {
	double value = 0;
	for (int k = 0; k < j2nonzero[i].length; k++) {
	    for (int l = 0; l < j2nonzero[j].length; l++) {
		if (j2nonzero[i][k] == j2nonzero[j][l])
		    value += j2matrix.get(i, j2nonzero[i][k])
			    * (weights[j2nonzero[i][k]] / 2)
			    * j2matrix.get(j, j2nonzero[i][k]);
	    }
	}
	return value;
    }

    private double computeResidual() {
	double value = 0;
	for (int i = 0; i < nangles; i++) {
	    value += gradient[i] * gradient[i];
	}
	for (int i = 0; i < ntri + 2 * nint; i++) {
	    value += constraints[i] * constraints[i];
	}
	return value;
    }

    /**
         * @return The mesh to unfold
         */
    public TriangleMesh getMesh() {
	return mesh;
    }

    /**
         * @param mesh
         *                The mesh to unfold
         */
    public void setMesh(TriangleMesh mesh) {
	this.mesh = mesh;
    }

    /**
         * Initilization job : -computes mesh angle -builds interior vertices
         * table
         */
    @SuppressWarnings("unchecked")
    private void initialize() {
	TriangleMesh.Vertex[] vertices = (Vertex[]) mesh.getVertices();
	TriangleMesh.Edge[] edges = mesh.getEdges();
	TriangleMesh.Face[] faces = mesh.getFaces();

	// System.out.println("mesh vertices :");
	// for (int i = 0; i < vertices.length; i++) {
	// System.out.println(i + ": " + vertices[i].r +" edge: " +
	// vertices[i].firstEdge + " nedges: " + vertices[i].edges );
	// }
	// System.out.println("mesh edges :");
	// for (int i = 0; i < edges.length; i++) {
	// System.out.println(i + ": f1: " + edges[i].f1 + " f2: " + edges[i].f2
	// + " v1: " + edges[i].v1 + " v2: " + edges[i].v2);
	// }
	// System.out.println("mesh faces :");
	// for (int i = 0; i < faces.length; i++) {
	// System.out.println(i + ": v1: " + faces[i].v1 + " v2: " + faces[i].v2
	// + " v3: " + faces[i].v3 );
	// System.out.println(i + ": e1: " + faces[i].e1 + " e2: " + faces[i].e2
	// + " e3: " + faces[i].e3 );
	// }
	// compute the mesh angles
	ntri = faces.length;
	nangles = 3 * ntri;
	angles = new double[nangles];
	Vec3 v1, v2, v3;
	for (int i = 0; i < ntri; i++) {
	    v1 = vertices[faces[i].v3].r.minus(vertices[faces[i].v1].r);
	    v2 = vertices[faces[i].v2].r.minus(vertices[faces[i].v1].r);
	    v3 = vertices[faces[i].v3].r.minus(vertices[faces[i].v2].r);
	    v1.normalize();
	    v2.normalize();
	    v3.normalize();
	    angles[i * 3] = Math.acos(v1.dot(v2));
	    angles[i * 3 + 1] = Math.acos(-v2.dot(v3));
	    angles[i * 3 + 2] = Math.acos(v1.dot(v3));
	}
	weights = new double[angles.length];
	for (int i = 0; i < angles.length; i++) {
	    weights[i] = /* 1.0 / */(angles[i] * angles[i]);
	}
	// setup interior vertices table
	invInteriorTable = new int[vertices.length];
	Vector interiorVerticesTable = new Vector();
	int count = 0;
	for (int i = 0; i < vertices.length; i++) {
	    if (edges[vertices[i].firstEdge].f1 != -1
		    && edges[vertices[i].firstEdge].f2 != -1) {
		// not a boundary edge
		interiorVerticesTable.add(new Integer(i));
		invInteriorTable[i] = count++;
	    } else
		invInteriorTable[i] = -1;
	}
	nint = interiorVerticesTable.size();
	interiorVertices = new int[nint];
	for (int i = 0; i < nint; i++) {
	    interiorVertices[i] = ((Integer) interiorVerticesTable.get(i))
		    .intValue();
	}
	interiorVerticesTable = null;
	angleTable = new int[nint][];
	angleMTable = new int[nint][];
	anglePTable = new int[nint][];
	for (int i = 0; i < nint; i++) {
	    int v = interiorVertices[i];
	    // System.out.println("interior vertex: " +
	    // interiorVertices[i]);
	    int[] ed = vertices[v].getEdges();
	    angleTable[i] = new int[ed.length];
	    angleMTable[i] = new int[ed.length];
	    anglePTable[i] = new int[ed.length];
	    // edges do not sequentially point to faces they delimit
	    // so we need to find the faces looking at both sides
	    // of an edge
	    int[] tris = new int[ed.length];
	    boolean add;
	    int index = 0;
	    int tri;
	    for (int j = 0; ((j < ed.length) && (index < ed.length)); j++) {
		add = true;
		tri = edges[ed[j]].f1;
		for (int k = 0; k < ed.length; k++) {
		    if (tris[k] == tri) {
			add = false;
			break;
		    }
		}
		if (add)
		    tris[index++] = tri;
		add = true;
		tri = edges[ed[j]].f2;
		for (int k = 0; k < ed.length; k++) {
		    if (tris[k] == tri) {
			add = false;
			break;
		    }
		}
		if (add)
		    tris[index++] = tri;
	    }
	    // now setup angles at interior vertices
	    // and plus and minus angles as defined in ABF++ paper
	    for (int j = 0; j < ed.length; j++) {
		tri = tris[j];
		if (v == faces[tri].v1) {
		    angleTable[i][j] = 3 * tri + 0;
		    anglePTable[i][j] = 3 * tri + 1;
		    angleMTable[i][j] = 3 * tri + 2;
		} else if (v == faces[tri].v2) {
		    angleTable[i][j] = 3 * tri + 1;
		    anglePTable[i][j] = 3 * tri + 2;
		    angleMTable[i][j] = 3 * tri + 0;
		} else if (v == faces[tri].v3) {
		    angleTable[i][j] = 3 * tri + 2;
		    anglePTable[i][j] = 3 * tri + 0;
		    angleMTable[i][j] = 3 * tri + 1;
		} else {
		    System.out
			    .println("pb setting angle tables: vertex not in face");
		}
	    }
	}
	var = new double[4 * ntri + 2 * nint];
	for (int i = 0; i < nangles; i++) {
	    var[i] = angles[i]; // start from current angle values
	}
	double anglesum;
	for (int i = 0; i < nint; i++) {
	    anglesum = 0;
	    for (int j = 0; j < angleTable[i].length; j++) {
		anglesum += angles[angleTable[i][j]];
	    }
	    if (anglesum > 0)
		for (int j = 0; j < angleTable[i].length; j++) {
		    var[angleTable[i][j]] *= 2 * Math.PI / anglesum;
		    angles[angleTable[i][j]] = var[angleTable[i][j]];
		}
	}
	for (int i = nangles; i < 4 * ntri + nint; i++) {
	    var[i] = 0; // Lagrange multipliers are set to zero
	}
	for (int i = 4 * ntri + nint; i < var.length; i++) {
	    var[i] = 1; // Length Lagrange multipliers are set to one
	}
	constraints = new double[ntri + 2 * nint];
	gradient = new double[nangles];
	bstar = new double[ntri + 2 * nint];
	solution = new double[4 * ntri + 2 * nint];
	jstar = new FlexCompRowMatrix(2 * nint, ntri);
	jstarstar = new FlexCompRowMatrix(2 * nint, 2 * nint);
	matrix = new FlexCompRowMatrix(2 * nint, 2 * nint);
	bb2vec = new DenseVector(2 * nint);
	// lambdaStar is computed once and for all
	lambdaStar = new double[ntri];
	lambdaStarInv = new double[ntri];
	for (int i = 0; i < ntri; i++) {
	    lambdaStar[i] = 0;
	    for (int j = 0; j < 3; j++) {
		lambdaStar[i] += weights[3 * i + j] / 2;
	    }
	    lambdaStarInv[i] = 1 / lambdaStar[i];
	}
	weightsVec = new DenseVector(weights);
	weightsVec.scale(0.5);
    }

    private void computeConstraints() {
	// triangle validity constraint
	for (int i = 0; i < ntri; i++) {
	    constraints[i] = -Math.PI;
	    for (int j = 0; j < 3; j++) {
		constraints[i] += var[3 * i + j];
	    }
	}
	// planarity constraint
	for (int i = 0; i < nint; i++) {
	    constraints[ntri + i] = -2 * Math.PI;
	    for (int j = 0; j < angleTable[i].length; j++) {
		constraints[ntri + i] += var[angleTable[i][j]];
	    }
	}
	// length constraint
	double mproduct, pproduct;
	for (int i = 0; i < nint; i++) {
	    pproduct = mproduct = 1;
	    for (int j = 0; j < angleTable[i].length; j++) {
		mproduct *= Math.sin(var[angleMTable[i][j]]);
		pproduct *= Math.sin(var[anglePTable[i][j]]);
	    }
	    constraints[ntri + nint + i] = pproduct - mproduct;
	}
    }

    private void computeJ2() {
	if (j2matrix == null) {
	    // first iteration
	    j2nonzero = new int[2 * nint][];
	    for (int i = 0; i < nint; i++) {
		j2nonzero[i] = new int[angleTable[i].length];
		j2nonzero[nint + i] = new int[anglePTable[i].length
			+ angleMTable[i].length];
		for (int j = 0; j < angleTable[i].length; j++) {
		    j2nonzero[i][j] = angleTable[i][j];
		    j2nonzero[nint + i][j] = anglePTable[i][j];
		    j2nonzero[nint + i][angleTable[i].length + j] = angleMTable[i][j];
		}
	    }
	    j2matrix = new CompRowMatrix(2 * nint, nangles, j2nonzero);
	}
	// zero out entries
	j2matrix.zero();
	// planarity constraint
	for (int i = 0; i < nint; i++) {
	    for (int j = 0; j < angleTable[i].length; j++) {
		j2matrix.set(i, angleTable[i][j], 1.0);
	    }
	}
	// length constraint
	double pproduct;
	double mproduct;
	for (int i = 0; i < nint; i++) {
	    for (int j = 0; j < angleTable[i].length; j++) {
		pproduct = mproduct = 1;
		for (int k = 0; k < angleTable[i].length; k++) {
		    if (k != j) {
			pproduct *= Math.sin(var[anglePTable[i][k]]);
			mproduct *= Math.sin(var[angleMTable[i][k]]);
		    } else {
			pproduct *= Math.cos(var[anglePTable[i][k]]);
			mproduct *= -Math.cos(var[angleMTable[i][k]]);
		    }
		}
		j2matrix.add(nint + i, anglePTable[i][j], pproduct);
		j2matrix.add(nint + i, angleMTable[i][j], mproduct);
	    }
	}
    }

    private void computeGradient() {
	for (int i = 0; i < ntri; i++) {
	    for (int j = 0; j < 3; j++) {
		gradient[3 * i + j] = (2 / weights[3 * i + j])
			* (var[3 * i + j] - angles[3 * i + j]);
		gradient[3 * i + j] += var[nangles + i];
	    }
	}
	for (int i = 0; i < nint; i++) {
	    for (int j = 0; j < angleTable[i].length; j++) {
		gradient[angleTable[i][j]] += j2matrix.get(i, angleTable[i][j])
			* var[4 * ntri + i];
		gradient[anglePTable[i][j]] += j2matrix.get(nint + i,
			anglePTable[i][j])
			* var[4 * ntri + nint + i];
		gradient[angleMTable[i][j]] += j2matrix.get(nint + i,
			angleMTable[i][j])
			* var[4 * ntri + nint + i];
	    }
	}
    }

    private double computeFunction() {
	double f = 0;
	for (int i = 0; i < nangles; i++) {
	    f += (1 / weights[i]) * (var[i] - angles[i]) * (var[i] - angles[i]);
	}
	// System.out.println("f1: " + f);
	for (int i = 0; i < ntri + 2 * nint; i++) {
	    // System.out.println(constraints[i] + " / " + var[nverts + i]);
	    f += var[nangles + i] * constraints[i];
	}
	// System.out.println("f2: " + f);
	return f;
    }

    @SuppressWarnings("unused")
    private void checkGradient() {
	double value, f1, f2;

	System.out.println(nangles + " " + 4 * ntri + " " + (4 * ntri + nint));
	for (int i = 0; i < nangles; i++) {
	    value = var[i];
	    var[i] = value - 0.0001;
	    computeConstraints();
	    f1 = computeFunction();
	    var[i] = value + 0.0001;
	    computeConstraints();
	    f2 = computeFunction();
	    var[i] = value;
	    value = (f2 - f1) / 0.0002;
	    System.out.println(i + " " + value + " " + gradient[i]);
	}
	System.out.println("/***** contraintes *****/");
	for (int i = nangles; i < var.length; i++) {
	    value = var[i];
	    var[i] = value - 0.0001;
	    computeConstraints();
	    f1 = computeFunction();
	    var[i] = value + 0.0001;
	    computeConstraints();
	    f2 = computeFunction();
	    var[i] = value;
	    value = (f2 - f1) / 0.0002;
	    System.out
		    .println(i + " " + value + " " + constraints[i - nangles]);
	}
	System.out.println("/***** fin gradient *****/");

    }
}
