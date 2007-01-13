package artofillusion.polymesh;

import java.util.Date;
import java.util.Vector;

import buoy.widget.BFrame;
import artofillusion.math.SVD;
import artofillusion.math.Vec3;
import artofillusion.object.MeshVertex;
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

    private double[][] J1matrix; // J1 as defined in ABF++ algorithm

    private double[][] J2matrix; // J2 as defined in ABF++ algorithm
    
    private CompRowMatrix sparseJ2matrix;
    
    private int[][] J2nz;

    private int[][] angleTable; // angle references for each interior vertex

    private int[][] anglePTable; // angle references for each interior

    // vertex, minus one (see ABF++)

    private int[][] angleMTable; // angle references for each interior

    // vertex, plus one (see ABF++)

    private int[] interiorVertices; // interior vertices array

    private double[] constraints; // constraints values

    private int nint; // number of interior vertices

    private int ntri; // number of triangles

    private double[] bstar; // ABF++ vector
    
    private double[] solution; //solution to solver
    
    private double[]  bb2; 

    private double[][] mat1;

    private double[][] mat2;
        
    private int[] invInteriorTable;

    protected BFrame parentWindow;

    private boolean debug = false;

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

    public void unfold() {
	//dump mesh;
	TriangleMesh.Vertex[] vertices = (Vertex[]) mesh.getVertices();
	TriangleMesh.Edge[] edges = mesh.getEdges();
	TriangleMesh.Face[] faces = mesh.getFaces();
	int nedges = edges.length;
	int nfaces = faces.length;
	long solvetime, totaltime;
	long mat3time, bstartime, jstartime, jstarstartime, tmptime, solutiontime;
	long t = 0;
	solvetime = mat3time = bstartime = jstartime = tmptime = jstarstartime = solutiontime = 0;
	initialize();
	computeConstraints();
	computeSparseJ2();
	computeSparseGradient();	
	//	lambdaStar is computed once and for all
	double[] lambdaStar = new double[ntri];
	double[] lambdaStarInv = new double[ntri];
	for (int i = 0; i < ntri; i++) {
	    lambdaStar[i] = 0;
	    for (int j = 0; j < 3; j++) {
		lambdaStar[i] += weights[3*i+j]/2;
	    }
	    lambdaStarInv[i] = 1/lambdaStar[i];
	}
	int iter = 0;
	double residual = computeResidual();
	System.out.println("Initial residual: " + residual);
	//checkGradient();
	FlexCompRowMatrix sparseJstar = new FlexCompRowMatrix(2*nint, ntri);
	FlexCompRowMatrix sparseJstarstar = new FlexCompRowMatrix(2*nint, 2*nint);
	//FlexCompRowMatrix newsparseJstarstar = new FlexCompRowMatrix(2*nint, 2*nint);
	FlexCompRowMatrix sparseMat3 = new FlexCompRowMatrix(2*nint, 2*nint);
	FlexCompRowMatrix newsparseMat3 = new FlexCompRowMatrix(2*nint, 2*nint);
	DenseVector newbb2 = new DenseVector(2*nint);
	int ind[];
	int jnd[];
	double[] idata;
	double[] jdata;
	SparseVector JstarVecI;
	SparseVector JstarVecJ;
	SparseVector JstarstarVec;
	DenseVector weightsVec = new DenseVector(weights);
	weightsVec.scale(0.5);
	double value;    
	boolean firstIter = true;
	//iteration
	totaltime = new Date().getTime();
	while (residual > 1.0e-6) {
	    //bstar computation
	    t = new Date().getTime();
	    for (int i = 0; i < ntri; i++) {
		bstar[i] = constraints[i];
		for (int j = 0; j < 3; j++) {
		    bstar[i] += (weights[3*i + j] / 2) * -gradient[3*i + j];
		}
	    }
	    for (int i = 0; i < nint; i++) {
		bstar[ntri + i] = constraints[ntri+i];
		bstar[ntri + nint + i] = constraints[ntri+nint+i];
		for (int j = 0; j < angleTable[i].length; j++) {
		    bstar[ntri + i] += sparseJ2matrix.get(i,angleTable[i][j]) * (weights[angleTable[i][j]] / 2)*-gradient[angleTable[i][j]];
		    bstar[ntri + nint + i] += sparseJ2matrix.get(nint + i,anglePTable[i][j]) * (weights[anglePTable[i][j]] / 2)*-gradient[anglePTable[i][j]];
		    bstar[ntri + nint + i] += sparseJ2matrix.get(nint + i,angleMTable[i][j]) * (weights[angleMTable[i][j]] / 2)*-gradient[angleMTable[i][j]];		
		}
	    }
	    bstartime += new Date().getTime() - t;
	    t = new Date().getTime();
	    //jstar computation
	    sparseJstar.zero();
	    int index;
	    for (int i = 0; i < 2*nint; i++) {
		for (int j = 0; j < J2nz[i].length; j++) {
		    index = J2nz[i][j]/3;
		    sparseJstar.add(i, index, sparseJ2matrix.get(i, J2nz[i][j])*(weights[J2nz[i][j]]/2) );
		}
	    }
	    if (firstIter) {
		sparseJstar.compact();
	    }
	    jstartime += new Date().getTime() - t;
//		   new jstarstar computation
	    t = new Date().getTime();
	    sparseJstarstar.zero();
//	    newsparseJstarstar.zero();
//	    for (int i = 0; i < 2*nint; i++) {
//		for (int j = 0; j < 2*nint; j++) {
//		    for (int k = 0; k < J2nz[i].length; k++) {
//			for (int l = 0; l < J2nz[j].length; l++) {
//			    if (J2nz[i][k] == J2nz[j][l])
//			sparseJstarstar.add(i, j, sparseJ2matrix.get(i,J2nz[i][k])*(weights[J2nz[i][k]]/2)*sparseJ2matrix.get(j, J2nz[i][k]) );
//		    
//			}
//		    }
//		}
//	    }
	    for (int e = 0; e < nedges; e++) {
		int i = invInteriorTable[edges[e].v1];
		int j = invInteriorTable[edges[e].v2];
		if ( i == -1 || j == -1 )
		    continue;
		value = jstarstarCompute(i ,j);
		sparseJstarstar.set(i, j, value);
		sparseJstarstar.set(j, i, value);
		i += nint;
		value = jstarstarCompute(i ,j);
		sparseJstarstar.set(i, j, value);
		sparseJstarstar.set(j, i, value);
		i -= nint;
		j += nint;
		value = jstarstarCompute(i ,j);
		sparseJstarstar.set(i, j, value);
		sparseJstarstar.set(j, i, value);
		i += nint;
		value = jstarstarCompute(i ,j);
		sparseJstarstar.set(i, j, value);
		sparseJstarstar.set(j, i, value);
	    }
	    for (int e = 0; e < nint; e++) {
		value = jstarstarCompute(e ,e);
		sparseJstarstar.set(e, e, value);
		value = jstarstarCompute(e + nint,e);
		sparseJstarstar.set(e +nint, e, value);
		value = jstarstarCompute(e ,e + nint);
		sparseJstarstar.set(e, e + nint, value);
		value = jstarstarCompute(e + nint ,e + nint);
		sparseJstarstar.set(e + nint, e + nint, value);
	    }
	    if (firstIter) {
		sparseJstarstar.compact();
	    }
	    jstarstartime += new Date().getTime() - t;
	    t = new Date().getTime();
	    sparseMat3.zero();
	    newbb2.zero();
//	    for (int i = 0; i < 2*nint; i++) {
//		JstarVecI = sparseJstar.getRow(i);
//		ind = JstarVecI.getIndex();
//		idata = JstarVecI.getData();
//		for (int j = 0; j < 2*nint; j++) {
//		    JstarVecJ = sparseJstar.getRow(j);
//		    jnd = JstarVecJ.getIndex();
//		    jdata = JstarVecJ.getData();
//		    for (int k = 0; k < ind.length; k++) {
//			for (int l = 0; l < jnd.length; l++) {
//			    if (ind[k] == jnd[l]) {
//			    sparseMat3.add(i, j, idata[k]*lambdaStarInv[ind[k]]*jdata[l] );
//			    }
//			}
//		    }
//		}
//		JstarstarVec = sparseJstarstar.getRow(i);
//		jnd = JstarstarVec.getIndex();
//		jdata = JstarstarVec.getData();
//		for (int j = 0; j < jnd.length; j++) {
// 		    sparseMat3.add(i, jnd[j], -jdata[j] );
//		}
//		newbb2.set(i, -bstar[i + ntri]);
//		for (int k = 0; k < ind.length; k++) {
//		    newbb2.add(i, idata[k]*lambdaStarInv[ind[k]]*bstar[ind[k]]);
//		}
//		//System.out.println(i + " " + bb2[i] + ":" + newbb2.get(i));
//	    }
//	    sparseMat3.compact();
//	    mat3time += new Date().getTime() - t;
//	    t = new Date().getTime();
	    sparseMat3.zero(); 
//	    for (int f = 0; f < nfaces; f++) {
//		int v1 = invInteriorTable[faces[f].v1];
//		int v2 = invInteriorTable[faces[f].v2];
//		if (v1 != -1 && v2 != -1) {
//		    addToSparseMat3(sparseMat3, sparseJstar, lambdaStarInv, v1, v2);
//		    addToSparseMat3(sparseMat3, sparseJstar, lambdaStarInv, v1+nint, v2);
//		    addToSparseMat3(sparseMat3, sparseJstar, lambdaStarInv, v1, v2 + nint);
//		    addToSparseMat3(sparseMat3, sparseJstar, lambdaStarInv, v1 + nint, v2 + nint);
//		}
//		v1 = invInteriorTable[faces[f].v2];
//		v2 = invInteriorTable[faces[f].v3];
//		if (v1 != -1 && v2 != -1) {
//		    addToSparseMat3(sparseMat3, sparseJstar, lambdaStarInv, v1, v2);
//		    addToSparseMat3(sparseMat3, sparseJstar, lambdaStarInv, v1+nint, v2);
//		    addToSparseMat3(sparseMat3, sparseJstar, lambdaStarInv, v1, v2 + nint);
//		    addToSparseMat3(sparseMat3, sparseJstar, lambdaStarInv, v1 + nint, v2 + nint);
//		}
//		v1 = invInteriorTable[faces[f].v3];
//		v2 = invInteriorTable[faces[f].v1];
//		if (v1 != -1 && v2 != -1) {
//		    addToSparseMat3(sparseMat3, sparseJstar, lambdaStarInv, v1, v2);
//		    addToSparseMat3(sparseMat3, sparseJstar, lambdaStarInv, v1+nint, v2);
//		    addToSparseMat3(sparseMat3, sparseJstar, lambdaStarInv, v1, v2 + nint);
//		    addToSparseMat3(sparseMat3, sparseJstar, lambdaStarInv, v1 + nint, v2 + nint);
//		}
//	    }
	    for (int e = 0; e < nedges; e++) {
		int i = invInteriorTable[edges[e].v1];
		int j = invInteriorTable[edges[e].v2];
		if ( i == -1 || j == -1 )
		    continue;
		addToSparseMat3(sparseMat3, sparseJstar, lambdaStarInv, i, j);
		addToSparseMat3(sparseMat3, sparseJstar, lambdaStarInv, i + nint, j);
		addToSparseMat3(sparseMat3, sparseJstar, lambdaStarInv, i, j + nint);
		addToSparseMat3(sparseMat3, sparseJstar, lambdaStarInv, i + nint, j + nint);
	    }
	    for (int i = 0; i < nint; i++) {
		addToSparseMat3(sparseMat3, sparseJstar, lambdaStarInv, i );
		addToSparseMat3(sparseMat3, sparseJstar, lambdaStarInv, i + nint, i);
		//addToSparseMat3(sparseMat3, sparseJstar, lambdaStarInv, i, i + nint);
		addToSparseMat3(sparseMat3, sparseJstar, lambdaStarInv, i + nint);
		JstarstarVec = sparseJstarstar.getRow(i);
		jnd = JstarstarVec.getIndex();
		jdata = JstarstarVec.getData();
		for (int j = 0; j < jnd.length; j++) {
		    sparseMat3.add(i, jnd[j], -jdata[j] );
		}
		JstarstarVec = sparseJstarstar.getRow(i + nint);
		jnd = JstarstarVec.getIndex();
		jdata = JstarstarVec.getData();
		for (int j = 0; j < jnd.length; j++) {
		    sparseMat3.add(i + nint, jnd[j], -jdata[j] );
		}		
	    }
	    for (int i = 0; i < 2*nint; i++) {
		JstarVecI = sparseJstar.getRow(i);
		ind = JstarVecI.getIndex();
		idata = JstarVecI.getData();
		newbb2.set(i, -bstar[i + ntri]);
		for (int k = 0; k < ind.length; k++) {
		    newbb2.add(i, idata[k]*lambdaStarInv[ind[k]]*bstar[ind[k]]);
		}
		//System.out.println(i + " " + bb2[i] + ":" + newbb2.get(i));
	    }
	    sparseMat3.compact();
	    mat3time += new Date().getTime() - t;
//	    for (int i = 0; i < 2*nint; i++) {
//		System.out.print(i);
//		for (int j = 0; j < 2*nint; j++)
//		    System.out.print(" " + sparseMat3.get(i,j) );
//		System.out.println("");
//		System.out.print(i);
//		for (int j = 0; j < 2*nint; j++)
//		    System.out.print(" " + newsparseMat3.get(i,j) );
//		System.out.println("");
//	    }
	    t = new Date().getTime();
	    DenseVector bb2sol = new DenseVector(2*nint);
	    BiCGstab bicg = new BiCGstab(bb2sol);
	    try {
		bicg.solve(sparseMat3, newbb2, bb2sol);
	    } catch (IterativeSolverNotConvergedException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	    }
	    solvetime+= new Date().getTime() - t;
	    t = new Date().getTime();
	    double[] newsolution = new double[4*ntri + 2*nint];
	    double[] newbb2sol = bb2sol.getData();
	    for (int i = 0; i < 2*nint; i++) {
		newsolution[4*ntri+i]=newbb2sol[i];
	    }	    
	    for (int i = 0; i < ntri; i++) {
		newsolution[3*ntri + i] = lambdaStarInv[i] * bstar[i];
	    }
	    for (int j = 0; j < 2*nint; j++) {
		JstarVecJ = sparseJstar.getRow(j);
		jnd = JstarVecJ.getIndex();
		jdata = JstarVecJ.getData();
		for (int i = 0; i < jnd.length; i++) {
		    newsolution[3*ntri + jnd[i]] -= lambdaStarInv[jnd[i]] * jdata[i] * newbb2sol[j];
		}
	    }
	    for (int i = 0; i < ntri; i++) {
		for (int k = 0; k < 3; k++) {
		    newsolution[3*i+k] += newsolution[i + 3*ntri];
		}
	    }
	    for (int j = ntri; j < ntri + 2 * nint; j++) {
		for (int i = 0; i < J2nz[j-ntri].length; i++) {
		    newsolution[J2nz[j-ntri][i]] += sparseJ2matrix.get(j - ntri, J2nz[j-ntri][i]) * newsolution[j + 3*ntri];
		}
	    }
	    for (int i = 0; i < 3 * ntri; i++) {
		newsolution[i] = (weights[i] / 2) * (-gradient[i] - newsolution[i]);
	    }
	    solutiontime += t - new Date().getTime();
	    for (int i = 0; i < var.length; i++) {
		var[i] += newsolution[i];
	    }
	    computeConstraints();
	    computeSparseJ2();
	    computeSparseGradient();
	    residual = computeResidual();
	    System.out.println("residual: " + residual);
	    System.out.println("bstar time :" + (bstartime/1000.0));
	    System.out.println("jstar time :" + (jstartime/1000.0));
	    System.out.println("newmat3 time :" + (tmptime/1000.0));
	    System.out.println("mat3 time :" + (mat3time/1000.0));
	    System.out.println("solve time :" + (solvetime/1000.0));
	    System.out.println("solution time :" + (solutiontime/1000.0));
	    if (firstIter)
		firstIter = false;
	    ++iter;
	}
	firstIter = false;
	System.out.println("Iterations: " + iter);
	totaltime = new Date().getTime() - totaltime;
	System.out.println("residual: " + residual);
	System.out.println("bstar time :" + (bstartime/1000.0));
	System.out.println("jstar time :" + (jstartime/1000.0));
	System.out.println("jstarstar time :" + (jstarstartime/1000.0));
	System.out.println("mat3 time :" + (mat3time/1000.0));
	System.out.println("newmat3 time :" + (tmptime/1000.0)); 
	System.out.println("solve time :" + (solvetime/1000.0));
	System.out.println("solution time :" + (solutiontime/1000.0));
	System.out.println("total time :" + (totaltime/1000.0));
    }
    
    private void addToSparseMat3(FlexCompRowMatrix mat, FlexCompRowMatrix sparseJstar, double[] lambdaStarInv, int v1, int v2) {
	
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
		    mat.add(v1, v2, idata[k]*lambdaStarInv[ind[k]]*jdata[l] );
		    mat.add(v2, v1, idata[k]*lambdaStarInv[ind[k]]*jdata[l] );
		}
	    }
	}
    }
    
    private void addToSparseMat3(FlexCompRowMatrix mat, FlexCompRowMatrix sparseJstar, double[] lambdaStarInv, int v1) {
	
	int ind[];
	double[] idata;
	SparseVector JstarVecI;
	JstarVecI = sparseJstar.getRow(v1);
	ind = JstarVecI.getIndex();
	idata = JstarVecI.getData();
	for (int k = 0; k < ind.length; k++) {
		    mat.add(v1, v1, idata[k]*lambdaStarInv[ind[k]]*idata[k] );
	}
    }
    
    private double jstarstarCompute(int i, int j) {
	double value = 0;
	for (int k = 0; k < J2nz[i].length; k++) {
	    for (int l = 0; l < J2nz[j].length; l++) {
		if (J2nz[i][k] == J2nz[j][l])
		    value += sparseJ2matrix.get(i,J2nz[i][k])*(weights[J2nz[i][k]]/2)*sparseJ2matrix.get(j, J2nz[i][k]);
	    }
	}
	return value;
    }
    public void unfold_check() {
	//dump mesh;
	long t1, t2, t2b, t3, t4, t;
	t = t4 = t3 = t2 = t2b = t1 = 0;
	initialize();
	computeConstraints();
	computeJ2();
	computeGradient();
	int nonZero, oldZero;
	nonZero = nonZero = 0;
	int colNonZero = 0;
	int rowNonZero = 0;
	for (int i = 0; i < 2*nint; i++) {
	    oldZero = nonZero;
	    for (int j =0; j < 3*ntri; j++) {
		if (Math.abs(J2matrix[i][j]) > 0.0001)
		    nonZero++;
	    }
	    if (nonZero != oldZero)
		rowNonZero++;
	}
	for (int j =0; j < 3*ntri; j++) {
	    oldZero = 0;
	    for (int i = 0; i < 2*nint; i++) {
		if (Math.abs(J2matrix[i][j]) > 0.0001)
		    oldZero++;
	    }
	    if (oldZero != 0)
		colNonZero++;
	}
	System.out.println("J2 " + 2*nint + "x" + 3*ntri + " = " + 2*nint*3*ntri);
	System.out.println("J2 non zero " + nonZero);
	System.out.println("J2 row non zero " + rowNonZero);
	System.out.println("J2 col non zero " + colNonZero);
	
	int iter = 0;
	double residual = computeResidual();
	System.out.println("Initial residual: " + residual);
	//checkGradient();
	double lambda = 1;
	t1 = new Date().getTime();
	double[] lambdaStar = new double[ntri];
	double[] lambdaStarInv = new double[ntri];
	double[][] Jstar = new double[2*nint][ntri];
	double[][] Jstarstar = new double[2*nint][2*nint];
	double[][] mat3 = new double[2*nint][2*nint];
	double[] bb2 = new double[2*nint];
	t = new Date().getTime();
	//lambdaStar is computed once and for all
	for (int i = 0; i < ntri; i++) {
	    lambdaStar[i] = 0;
	    for (int j = 0; j < 3 * ntri; j++) {
		lambdaStar[i] += J1matrix[i][j]*(weights[j]/2)*J1matrix[i][j];
	    }
	    lambdaStarInv[i] = 1/lambdaStar[i];
	}
	while (residual > 1.0e-6) {
	    for (int i = 0; i < gradient.length; i++) {
		gradient[i] = -gradient[i];
	    }
	    for (int i = 0; i < constraints.length; i++) {
		constraints[i] = -constraints[i];
	    }
	    //bstar computation
	    for (int i = 0; i < ntri; i++) {
		for (int j = 0; j < 3 * ntri; j++) {
		    mat1[i][j] = J1matrix[i][j] * weights[j] / 2;
		}
	    }
	    for (int i = ntri; i < ntri + 2 * nint; i++) {
		for (int j = 0; j < 3 * ntri; j++) {
		    mat1[i][j] = J2matrix[i - ntri][j] * weights[j] / 2;
		}
	    }
	    double[] bstaror = new double[bstar.length];
	    for (int i = 0; i < ntri + 2 * nint; i++) {
		bstar[i] = 0;
		for (int j = 0; j < 3 * ntri; j++) {
		    bstar[i] += mat1[i][j] * gradient[j];
		}
		bstar[i] -= constraints[i];
		bstaror[i] = bstar[i];
	    }
	    //jstar computation
	    for (int i = 0; i < 2*nint; i++) {
		for (int j = 0; j < ntri; j++) {
		    Jstar[i][j] = 0;
		    for (int k = 0; k < 3*ntri; k++) {
			Jstar[i][j] += J2matrix[i][k]*(weights[k]/2)*J1matrix[j][k];
		    }
		}
	    }
	    //jstarstar computation
	    for (int i = 0; i < 2*nint; i++) {
		for (int j = 0; j < 2*nint; j++) {
		    Jstarstar[i][j] = 0;
		    for (int k = 0; k < 3*ntri; k++) {
			Jstarstar[i][j] += J2matrix[i][k]*(weights[k]/2)*J2matrix[j][k];
		    }
		}
	    }
	    
	    //mat3 and right hand vector computation
	    for (int i = 0; i < 2*nint; i++) {
		for (int j = 0; j < 2*nint; j++) {
		    mat3[i][j] = 0;
		    for (int k = 0; k < ntri; k++) {
			mat3[i][j] += Jstar[i][k]*lambdaStarInv[k]*Jstar[j][k];
		    }
		    mat3[i][j] -= Jstarstar[i][j];
		}
		bb2[i] = -bstar[i + ntri];
		for (int k = 0; k < ntri; k++) {
		    bb2[i] += Jstar[i][k]*lambdaStarInv[k]*bstar[k];
		}
	    }
	    nonZero = 0;
	    int[][] nz = new int[2*nint][];
	    Vector vect = new Vector();
	    for (int i = 0; i < 2*nint; i++) {
		for (int j =0; j < 2*nint; j++) {
		    if (Math.abs(mat3[i][j]) > 1e-8)
			vect.add(new Integer(j));
		}
		nonZero += vect.size();
		nz[i] = new int[vect.size()];
		for (int j =0; j < vect.size(); j++) {
		    nz[i][j] = ((Integer)vect.get(j)).intValue();
		}
		vect.clear();
	    }
	    System.out.println("mat3 nonzero : " + nonZero);
	    nonZero = 0;
	    for (int i = 0; i < 2*nint; i++) {
		nonZero += nz[i].length;
	    }
	    System.out.println("rowCol nonzero : " + nonZero);
	    
//	    for (int i = 0; i < 2*nint; i++) {
//		    System.out.print(i);
//		    for (int j = 0; j < 2*nint; j++)
//			System.out.print(" " + mat3[i][j]);
//		    System.out.println("");
//		}
	    t4 += t - new Date().getTime();
	    CompRowMatrix rowMat = new CompRowMatrix(2*nint, 2*nint, nz);
	    for (int i = 0; i < 2*nint; i++) {
		for (int j =0; j < nz[i].length; j++) {
		    rowMat.set(i, nz[i][j], mat3[i][nz[i][j]]);
		}
	    }
	    DenseVector bb2vec = new DenseVector(bb2);
	    double[] tmpv = new double[bb2.length];
	    for (int i = 0; i < tmpv.length; i++)
		tmpv[i] = -0.1;
	    DenseVector bb2sol = new DenseVector(tmpv);
	    BiCGstab bicg = new BiCGstab(bb2sol);
	    t = new Date().getTime();
	    try {
		bicg.solve(rowMat, bb2vec, bb2sol);
	    } catch (IterativeSolverNotConvergedException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	    }
	    t2b+= new Date().getTime() - t;
	    t = new Date().getTime();
	    for (int i = 0; i < 2*nint; i++)
		System.out.println("rh: " + i + " : " + bb2[i]);
	    SVD.solve(mat3, bb2);
	    for (int i = 0; i < 2*nint; i++)
		System.out.println("solution: " + i + " : " + bb2[i]);
	    t2 += (new Date().getTime() -t);
	    double value;
	    double[] solution = new double[4*ntri + 2*nint];
	    for (int i  = 0; i < 2*nint; i++)
		solution[4*ntri + i] = bb2[i];
	    //bb2 = bb2sol .getData();
	    for (int i = 0; i < ntri; i++) {
		solution[3*ntri + i] = lambdaStarInv[i] * bstar[i];
		for (int j = 0; j < 2*nint; j++) {
		    solution[3*ntri + i] -= lambdaStarInv[i] * Jstar[j][i] * bb2[j];
		}
	    }
	    // solution now contains delta lambda
	    for (int i = 0; i < 3 * ntri; i++) {
		value = 0;
		for (int j = 0; j < ntri; j++) {
		    value += J1matrix[j][i] * solution[j + 3*ntri];
		}
		for (int j = ntri; j < ntri + 2 * nint; j++) {
		    value += J2matrix[j - ntri][i] * solution[j + 3*ntri];
		}
		solution[i] = (weights[i] / 2) * (gradient[i] - value);
		//var[i] += (weights[i] / 2) * (gradient[i] - value);
	    }
	    t = new Date().getTime();
	    for (int i = 0; i < var.length; i++) {
		var[i] += solution[i];
		System.out.println(solution[i]);
	    }
	    computeConstraints();
	    computeJ2();
	    computeGradient();
	    residual = computeResidual();
	    t3 += (new Date().getTime() -t);
	    System.out.println("residual: " + residual +" lambda: " + lambda);
	    System.out.println("Solver time :" + (t2/1000.0) + " " + (t2b/1000.0));
	    System.out.println("Delta time :" + (t3/1000.0) );
	    System.out.println("Matrix comp time :" + (-t4/1000.0) );
	    residual = 0;
	    ++iter;   
	}
	System.out.println("Iterations: " + iter);
	t = new Date().getTime();
	System.out.println("Total time :" + ((t - t1)/1000.0) );
	System.out.println("Solver time :" + (t2/1000.0) + " " + (t2b/1000.0));
	System.out.println("Delta time :" + (t3/1000.0) );
	System.out.println("Matrix comp time :" + (-t4/1000.0) );
//	for (int i = 0; i < 3 * ntri; i++) {
//	    System.out.println(i + " " + var[i] + " " + angles[i]);
//	}
//	for (int i = 3*ntri; i < 4 * ntri + 2 * nint; i++) {
//	    System.out.println(i + " " + var[i] + " " + constraints[i - 3*ntri]);
//	} 
    }
    
    public void unfold2() {
	long t1, t2, t3, t;
	t = t3 = t2 = t1 = 0;
	initialize();
	computeConstraints();
	computeJ2();
	
	computeGradient();
	int iter = 0;
	double residual = computeResidual();
	System.out.println("Initial residual: " + Math.sqrt(residual));
	checkGradient();
	double lambda = 1;
	t1 = new Date().getTime();
	while (residual > 1.0e-8) {
	    for (int i = 0; i < gradient.length; i++) {
		gradient[i] = -gradient[i];
	    }
	    for (int i = 0; i < constraints.length; i++) {
		constraints[i] = -constraints[i];
	    }
	    for (int i = 0; i < ntri; i++) {
		for (int j = 0; j < 3 * ntri; j++) {
		    mat1[i][j] = J1matrix[i][j] * weights[j] / 2;
		}
	    }
	    for (int i = ntri; i < ntri + 2 * nint; i++) {
		for (int j = 0; j < 3 * ntri; j++) {
		    mat1[i][j] = J2matrix[i - ntri][j] * weights[j] / 2;
		}
	    }
	    double[] bstaror = new double[bstar.length];
	    for (int i = 0; i < ntri + 2 * nint; i++) {
		bstar[i] = 0;
		for (int j = 0; j < 3 * ntri; j++) {
		    bstar[i] += mat1[i][j] * gradient[j];
		}
		bstar[i] -= constraints[i];
		bstaror[i] = bstar[i];
	    }
	    for (int i = 0; i < ntri + 2 * nint; i++) {
		for (int j = 0; j < ntri; j++) {
		    mat2[i][j] = 0;
		    for (int k = 0; k < 3 * ntri; k++) {
			mat2[i][j] += mat1[i][k] * J1matrix[j][k];
		    }
		}
		for (int j = ntri; j < ntri + 2 * nint; j++) {
		    mat2[i][j] = 0;
		    for (int k = 0; k < 3 * ntri; k++) {
			mat2[i][j] += mat1[i][k] * J2matrix[j - ntri][k];
		    }
		}
	    }
	    t = new Date().getTime();
	    SVD.solve(mat2, bstar);
	    t2 += (new Date().getTime() -t);
	    double value;
	    double[] solution = new double[4*ntri + 2*nint];
	    for (int i = 0; i < ntri + 2 * nint; i++) {
		solution[3*ntri + i] = bstar[i];
		//var[i + 3 * ntri] += bstar[i];
	    }
	    // bstar now contains delta lambda
	    for (int i = 0; i < 3 * ntri; i++) {
		value = 0;
		for (int j = 0; j < ntri; j++) {
		    value += J1matrix[j][i] * bstar[j];
		}
		for (int j = ntri; j < ntri + 2 * nint; j++) {
		    value += J2matrix[j - ntri][i] * bstar[j];
		}
		solution[i] = (weights[i] / 2) * (gradient[i] - value);
		//var[i] += (weights[i] / 2) * (gradient[i] - value);
	    }
//	    System.out.println("solution :");
//	    for (int i = 0; i < 3 * ntri; i++) {
//		System.out.println( i + ": " + solution[i]);
//	    }
//	    computeConstraints();
//	    computeJ2();
//	    computeGradient();
	    /**** solution check ****/
	    //residual = 0; //break loop
	    //setting up old variables
//	    for (int i = 0; i < 3*ntri; i++) {
//		var[i] = angles[i];
//	    }
//	    for (int i = 0; i < ntri + 2 * nint; i++) {
//		var[i + 3*ntri] = 0;
//	    }
//	    computeConstraints();
//	    computeJ2();
//	    computeGradient();
//	    for (int i = 0; i < 3*ntri; i++) {
//		value = 2 * solution[i] / weights[i];
//		for (int j = 0; j < ntri; j++) {
//		    value += J1matrix[j][i]*solution[j + 3*ntri];  
//		}
//		for (int j = 0; j < 2 * nint; j++) {
//		    value += J2matrix[j][i]*solution[j + 4*ntri];  
//		}
//		System.out.println( i + " " + gradient[i] + " " + value);
//	    }
//	    for (int i = 0; i < ntri; i++) {
//		value = 0;
//		for (int j = 0; j < 3*ntri; j++) {
//		    value += J1matrix[i][j]*solution[j];  
//		}
//		System.out.println( (i + 3*ntri) + " " + constraints[i] + " " + value);
//	    }
//	    for (int i = 0; i < 2*nint; i++) {
//		value = 0;
//		for (int j = 0; j < 3*ntri; j++) {
//		    value += J2matrix[i][j]*solution[j];  
//		}
//		System.out.println( (i + 4*ntri) + " " + constraints[i + ntri] + " " + value);
//	    }
	    double newResidual = 0;
	    double[] oldVar = new double[var.length];
	    for (int i = 0; i < var.length; i++) {
		oldVar[i] = var[i];
	    }
	    lambda *= 2;
	    t = new Date().getTime();
	    do {
		lambda *= 0.5;
		for (int i = 0; i < var.length; i++) {
			var[i] = oldVar[i] + solution[i]*lambda;
		}
		computeConstraints();
		computeJ2();
		computeGradient();
		newResidual = computeResidual();
	    } while (newResidual > residual && lambda > 0.1);
	    t3 += (new Date().getTime() -t);
	    if (newResidual < residual && lambda < 1)
		lambda *= 2;
	    residual = newResidual;
//	    computeConstraints();
//	    computeJ2();
//	    computeGradient();
//	    System.out.println("Nouvelle solution: ");
//	    for (int i = 0; i < 3 * ntri; i++) {
//		System.out.println(i + " " + var[i] + " " + angles[i]);
//	    }
//	    for (int i = 3*ntri; i < 4 * ntri + 2 * nint; i++) {
//		System.out.println(i + " " + var[i] + " " + constraints[i - 3*ntri]);
//	    }
//	    residual = computeResidual();
	    System.out.println("residual: " + Math.sqrt(residual) +" lambda: " + lambda);
	    ++iter;   
	}
	System.out.println("Iterations: " + iter);
	t = new Date().getTime();
	System.out.println("Total time :" + ((t - t1)/1000.0) );
	System.out.println("Solver time :" + (t2/1000.0) );
	System.out.println("Delta time :" + (t3/1000.0) );
	for (int i = 0; i < 3 * ntri; i++) {
	    System.out.println(i + " " + var[i] + " " + angles[i]);
	}
//	for (int i = 3*ntri; i < 4 * ntri + 2 * nint; i++) {
//	    System.out.println(i + " " + var[i] + " " + constraints[i - 3*ntri]);
//	} 
    }

    private double computeResidual() {
	double value = 0;
	for (int i = 0; i < 3 * ntri; i++) {
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
    private void initialize() {
	TriangleMesh.Vertex[] vertices = (Vertex[]) mesh.getVertices();
	TriangleMesh.Edge[] edges = mesh.getEdges();
	TriangleMesh.Face[] faces = mesh.getFaces();

//	System.out.println("mesh vertices :");
//	for (int i = 0; i < vertices.length; i++) {
//	    System.out.println(i + ": " + vertices[i].r +" edge: " + vertices[i].firstEdge  + " nedges: " + vertices[i].edges );
//	}
//	System.out.println("mesh edges :");
//	for (int i = 0; i < edges.length; i++) {
//	    System.out.println(i + ":    f1: " + edges[i].f1 + "  f2: " + edges[i].f2 + "  v1: " + edges[i].v1 + "  v2: " + edges[i].v2);
//	}
//	System.out.println("mesh faces :");
//	for (int i = 0; i < faces.length; i++) {
//	    System.out.println(i + ":   v1: " + faces[i].v1 + "  v2: " + faces[i].v2 + "  v3: " + faces[i].v3 );
//	    System.out.println(i + ":   e1: " + faces[i].e1 + "  e2: " + faces[i].e2 + "  e3: " + faces[i].e3 );
//	}
	// compute the mesh angles
	ntri = faces.length;
	angles = new double[3 * ntri];
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
	    weights[i] = /*1.0 /*/ (angles[i] * angles[i]);
	}
	// setup interior vertices table
	invInteriorTable = new int[vertices.length];
	Vector interiorVerticesTable = new Vector();
	int count=0;
	for (int i = 0; i < vertices.length; i++) {
	    if (edges[vertices[i].firstEdge].f1 != -1
		    && edges[vertices[i].firstEdge].f2 != -1) {
		// not a boundary edge
		interiorVerticesTable.add(new Integer(i));
		invInteriorTable[i] = count++;
	    }
	    else
		invInteriorTable[i] = -1;
	}
	nint = interiorVerticesTable.size();
	interiorVertices = new int[nint];
	for (int i = 0; i < nint; i++) {
	    interiorVertices[i] = ((Integer) interiorVerticesTable.get(i))
		    .intValue();
	}
	interiorVerticesTable = null;
	//J1matrix = new double[ntri][3 * ntri];
	//J2matrix = new double[2 * nint][3 * ntri];
//	for (int i = 0; i < ntri; i++) {
//	    for (int j = 0; j < 3; j++) {
//		J1matrix[i][3 * i + j] = 1.0;
//	    }
//	}
	angleTable = new int[nint][];
	angleMTable = new int[nint][];
	anglePTable = new int[nint][];
	for (int i = 0; i < nint; i++) {
	    int v = interiorVertices[i];
	    //System.out.println("interior vertex: " + interiorVertices[i]);
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
		//System.out.println("angles: " + angleTable[i][j] + " " +anglePTable[i][j] + " " + angleMTable[i][j]);
		//J2matrix[i][angleTable[i][j]] = 1.0;
	    }
	}
	var = new double[4 * ntri + 2 * nint];
	for (int i = 0; i < 3 * ntri; i++) {
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
			var[angleTable[i][j]] *= 2*Math.PI/anglesum;
			angles[angleTable[i][j]] = var[angleTable[i][j]];
			//var[angleTable[i][j]] *= 0.5;
		    }
	}
//	System.out.println("/***** variables *****/");
//	for (int i = 0; i < 3 * ntri; i++) {
//	    System.out.println(i + " " + var[i] + " " + angles[i]);
//	}
        for (int i = 3 * ntri; i < 4*ntri + nint; i++) {
	    var[i] = 0; // Lagrange multipliers are set to zero
	}
        for (int i = 4*ntri + nint; i < var.length; i++) {
	    var[i] = 1; // Lagrange multipliers are set to zero
	}
	constraints = new double[ntri + 2 * nint];
	gradient = new double[3 * ntri];
	bstar = new double[ntri + 2 * nint];
	//mat1 = new double[ntri + 2 * nint][3 * ntri];
	//mat2 = new double[ntri + 2 * nint][ntri + 2 * nint];
	solution = new double[4*ntri + 2*nint];
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
		//System.out.println("plus :" + anglePTable[i][j] + " " + var[anglePTable[i][j]]);
		//System.out.println("moins :" + angleMTable[i][j] + " " + var[angleMTable[i][j]]);
		mproduct *= Math.sin(var[angleMTable[i][j]]);
		pproduct *= Math.sin(var[anglePTable[i][j]]);
	    }
	    constraints[ntri + nint + i] = pproduct - mproduct;
	    //System.out.println("contraintes length : " + i + " " + constraints[ntri + nint + i] + " " + pproduct + " " + mproduct);
	}
    }

    private void computeSparseJ2() {
	if (sparseJ2matrix == null) {
	    //first iteration
	    J2nz = new int[2*nint][];
	    for (int i = 0; i < nint; i++) {
		J2nz[i] = new int[angleTable[i].length];
		J2nz[nint + i] = new int[anglePTable[i].length+angleMTable[i].length];
		for (int j = 0; j < angleTable[i].length; j++)
		{
		    J2nz[i][j] = angleTable[i][j];
		    J2nz[nint+i][j] = anglePTable[i][j];
		    J2nz[nint+i][angleTable[i].length+j] = angleMTable[i][j];
		}
	    }
	    sparseJ2matrix = new CompRowMatrix(2*nint, 3*ntri, J2nz);
	}
//	else {
	    //zero out entries
	    sparseJ2matrix.zero();
//	}
	// planarity constraint
	for (int i = 0; i < nint; i++) {
	    for (int j = 0; j < angleTable[i].length; j++) {
		sparseJ2matrix.set(i,angleTable[i][j], 1.0);
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
		sparseJ2matrix.add(nint + i, anglePTable[i][j], pproduct);
		sparseJ2matrix.add(nint + i, angleMTable[i][j], mproduct);
	    }
	}
    }
    
    private void computeJ2() {
	// planarity constraint
	for (int i = 0; i < nint; i++) {
	    for (int j = 0; j < angleTable[i].length; j++) {
		J2matrix[i][angleTable[i][j]] = 1.0;
	    }
	}
	// length constraint
	double pproduct;
	double mproduct;
	for (int i = 0; i < nint; i++) {
	    for (int j = 0; j < angles.length; j++) {
		J2matrix[nint + i][j] = 0;
	    }
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
		J2matrix[nint + i][anglePTable[i][j]] += pproduct;
		J2matrix[nint + i][angleMTable[i][j]] += mproduct;
	    }
	}
    }

    private void computeSparseGradient() {
	for (int i = 0; i < ntri; i++) {
	    for (int j = 0; j < 3; j++) {
		//System.out.println("gradient " + (3*i+j) );
		gradient[3 * i + j] = ( 2 / weights[3 * i + j] )
			* (var[3 * i + j] - angles[3 * i + j]);
		//System.out.print("agl " + gradient[3 * i + j] + " ");
		gradient[3 * i + j] += var[3 * ntri + i];
		//System.out.print("tri " + var[3 * ntri + i] + " ");

//		for (int k = 0; k < nint; k++) {
//		    gradient[3 * i + j] += J2matrix[k][3 * i + j]
//			    * var[4 * ntri + k];
//		    gradient[3 * i + j] += J2matrix[nint + k][3 * i + j]
//			    * var[4 * ntri + nint + k];
//		}
	    }
	}
	for (int i = 0; i < nint; i++) {
	    for (int j = 0; j < angleTable[i].length; j++) {
		gradient[angleTable[i][j]] += sparseJ2matrix.get(i, angleTable[i][j])*var[4*ntri+i];
		gradient[anglePTable[i][j]] += sparseJ2matrix.get(nint+i, anglePTable[i][j])*var[4*ntri+nint+i];
		gradient[angleMTable[i][j]] += sparseJ2matrix.get(nint+i, angleMTable[i][j])*var[4*ntri+nint+i];
	    }
	}
    }
    
    private void computeGradient() {
	for (int i = 0; i < ntri; i++) {
	    for (int j = 0; j < 3; j++) {
		//System.out.println("gradient " + (3*i+j) );
		gradient[3 * i + j] = ( 2 / weights[3 * i + j] )
			* (var[3 * i + j] - angles[3 * i + j]);
		//System.out.print("agl " + gradient[3 * i + j] + " ");
		gradient[3 * i + j] += var[3 * ntri + i];
		//System.out.print("tri " + var[3 * ntri + i] + " ");

//		for (int k = 0; k < nint; k++) {
//		    gradient[3 * i + j] += J2matrix[k][3 * i + j]
//			    * var[4 * ntri + k];
//		    gradient[3 * i + j] += J2matrix[nint + k][3 * i + j]
//			    * var[4 * ntri + nint + k];
//		
		double a, b;
		a = b = 0;
		for (int k = 0; k < nint; k++) {
		    a += J2matrix[k][3 * i + j]
			    * var[4 * ntri + k];
		    b += J2matrix[nint + k][3 * i + j]
			    * var[4 * ntri + nint + k];
		}
		//System.out.print("plan " + a + " ");
		//System.out.println("length " + b + " ");
		gradient[3 * i + j] += a + b;
	    }
	}
    }

    private double computeFunction() {
	double f = 0;
	for (int i = 0; i < 3 * ntri; i++) {
	    f += (1 / weights[i] ) * (var[i] - angles[i]) * (var[i] - angles[i]);
	}
	//System.out.println("f1: " + f);
	for (int i = 0; i < ntri + 2 * nint; i++) {
	    //System.out.println(constraints[i] +  " / " + var[3*ntri + i]);
	    f += var[3 * ntri + i] * constraints[i];
	}
	//System.out.println("f2: " + f);
	return f;
    }

    private void checkGradient() {
	double value, f1, f2;

	System.out.println(3 * ntri + " " + 4 * ntri + " " + (4 * ntri + nint));
	for (int i = 0; i < 3 * ntri; i++) {
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
	for (int i = 3 * ntri; i < var.length; i++) {
	    value = var[i];
	    var[i] = value - 0.0001;
	    computeConstraints();
	    f1 = computeFunction();
	    var[i] = value + 0.0001;
	    computeConstraints();
	    f2 = computeFunction();
	    var[i] = value;
	    value = (f2 - f1) / 0.0002;
	    System.out.println(i + " " + value + " "
		    + constraints[i - 3 * ntri]);
	}
	System.out.println("/***** fin gradient *****/");

    }
}
