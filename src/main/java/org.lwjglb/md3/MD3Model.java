package org.lwjglb.md3;




import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Matrix4f;
import java.awt.geom.*;
import java.awt.Image;
import java.awt.Graphics2D;
import java.awt.image.*;
import java.io.*;
import java.nio.*;
import java.util.StringTokenizer;
import org.lwjgl.*;
import org.lwjgl.opengl.*;
import org.lwjglb.entities.Camera;
import org.lwjglb.renderEngine.MasterRenderer;
import org.lwjglb.util.Maths;

public final class MD3Model {

	
	private final class MD3Header {
		String	fileID;							
		int		version;						
		String	strFile;						
		int		numFrames;						
		int		numTags;						
		int		numMeshes;						
		int		numMaxSkins;					
		int		headerSize;						
		int		tagStart;						
		int		tagEnd;							
		int		fileSize;						

		protected MD3Header() {
			fileID		= byte2string(4);	m_FilePointer += 4;
			version		= byte2int();
			strFile		= byte2string(68);	m_FilePointer += 68;
			numFrames	= byte2int();
			numTags		= byte2int();
			numMeshes	= byte2int();
			numMaxSkins = byte2int();
			headerSize	= byte2int();
			tagStart	= byte2int();
			tagEnd		= byte2int();
			fileSize	= byte2int();
		}
	
	};

	private final class MD3MeshInfo {
		String	meshID;							
		String	strName;						
		int		numMeshFrames;					
		int		numSkins;						
		int     numVertices;					
		int		numTriangles;					
		int		triStart;						
		int		headerSize;						
		int     uvStart;						
		int		vertexStart;					
		int		meshSize;						

		protected MD3MeshInfo() {
			meshID			= byte2string(4);	m_FilePointer += 4;
			strName			= byte2string(68);	m_FilePointer += 68;
			numMeshFrames	= byte2int();
			numSkins		= byte2int();
			numVertices		= byte2int();
			numTriangles	= byte2int();
			triStart		= byte2int();
			headerSize		= byte2int();
			uvStart			= byte2int();
			vertexStart		= byte2int();
			meshSize		= byte2int();
		}
	};

	private final class MD3Tag {
		String	 strName;						
		Vector3f vPosition = new Vector3f();	
		float[]	 rotation = new float[9];		

		protected MD3Tag() {
			strName = byte2string(64);	m_FilePointer += 64;
			vPosition.x = byte2float();
			vPosition.y = byte2float();
			vPosition.z = byte2float();
			for (int i=0; i < 9; i++ ) {
				rotation[i] = byte2float();
			}
		}
	};

	private final class MD3Bone	{
		float[]	mins = new float[3];			
		float[]	maxs = new float[3];			
		float[]	position = new float[3];		
		float	scale;							
		String	creator;						

		protected MD3Bone()	{
			mins[0]		= byte2float();
			mins[1]		= byte2float();
			mins[2]		= byte2float();
			maxs[0]		= byte2float();
			maxs[1]		= byte2float();
			maxs[2]		= byte2float();
			position[0] = byte2float();
			position[1] = byte2float();
			position[2] = byte2float();
			scale		= byte2float();
			creator		= byte2string(16);	m_FilePointer += 16;
		}
	};

	private class MD3Triangle {
	   short[] vertex = new short[3];			
	   int[] normal = new int[2];				

	   protected MD3Triangle() {
			vertex[0] = byte2short();
			vertex[1] = byte2short();
			vertex[2] = byte2short();
			normal[0] = byte2byte();
			normal[1] = byte2byte();
	   }
	};

	private final class MD3Face {
	   int[] vertexIndices = new int[3];	

	   protected MD3Face() {
			vertexIndices[0] = byte2int();
			vertexIndices[1] = byte2int();
			vertexIndices[2] = byte2int();
	   }
	};


	private final class MD3TexCoord {
	   float[] textureCoord = new float[2];		

	   protected MD3TexCoord() {
		   textureCoord[0] = byte2float();
		   textureCoord[1] = byte2float();
	   }
	};

		private final class MD3Skin {
		String strName;							

		protected MD3Skin()	{
			strName = byte2string(68);	m_FilePointer += 68;
		}
	};

	private final class Face {
		int[] vertIndex = new int[3];			
		int[] coordIndex = new int[3];			

		protected Face()
		{
		}
	};

	private final class MaterialInfo {
		String  strName;						
		String  strFile;						
		byte[]  color = new byte[3];			
		int   texureId;							
		float uTile;							
		float vTile;							
		float uOffset;							
		float vOffset;							

		protected MaterialInfo()
		{
		}
	};

	private final class Object3D {
		int  numOfVerts;						
		int  numOfFaces;						
		int  numTexVertex;						
		int  materialID;						
		boolean bHasTexture;					
		String strName;							
		Vector3f[]  pVerts;						
		Vector3f[]  pNormals;					
		Vector2f[]  pTexVerts;					
		Face[] pFaces;							

		int vaoID        = -1;
		int verticesVboID = -1;
		int texCoordsVboID = -1;

		protected Object3D()
		{
		}
	};


	public final class AnimationInfo {
		String strName;							
		int startFrame;							
		int endFrame;							
		int loopingFrames;						
		int framesPerSecond;					

		public AnimationInfo()
		{
		}
	};


	public final class Model3D {
		int numOfObjects;						
		int numOfMaterials;						
		FastVector pMaterials = new FastVector(); 
		FastVector pObject = new FastVector();	

		int numOfAnimations;					
		int currentAnim;						
		int currentFrame;						
		int nextFrame;							
		float t;								
		long lastTime;							
		FastVector pAnimations= new FastVector(); 

		int numOfTags;							
		Model3D[] pLinks;						
		MD3Tag[] pTags;							

		public Model3D()
		{
		}
	};


	public static final int kLower = 0;

	public static final int kUpper = 1;

	public static final int kHead = 2;

	public static final int kWeapon = 3;

	public static final int BOTH_DEATH1 = 0;

	public static final int BOTH_DEAD1 = 1;

	public static final int BOTH_DEATH2 = 2;

	public static final int BOTH_DEAD2 = 3;

	public static final int BOTH_DEATH3 = 4;

	public static final int BOTH_DEAD3 = 5;

	public static final int TORSO_GESTURE = 6;
	
	public static final int TORSO_ATTACK = 7;

	public static final int TORSO_ATTACK2 = 8;

	public static final int TORSO_DROP = 9;

	public static final int TORSO_RAISE = 10;

	public static final int TORSO_STAND = 11;

	public static final int TORSO_STAND2 = 12;

	public static final int LEGS_WALKCR = 13;

	public static final int LEGS_WALK = 14;

	public static final int LEGS_RUN = 15;

	public static final int LEGS_BACK = 16;

	public static final int LEGS_SWIM = 17;
	
	public static final int LEGS_JUMP = 18;

	public static final int LEGS_LAND = 19;

	public static final int LEGS_JUMPB = 20;

	public static final int LEGS_LANDB = 21;

	public static final int LEGS_IDLE = 22;

	public static final int LEGS_IDLECR = 23;

	public static final int LEGS_TURN = 24;

	public static final int MAX_ANIMATIONS = 200;

	private MD3Header m_Header;

	private MD3Skin m_pSkins[];

	private MD3TexCoord m_pTexCoords[];

	private MD3Face m_pTriangles[];

	private MD3Triangle m_pVertices[];

	private MD3Bone m_pBones[];

	private Model3D m_Head;

	private Model3D m_Upper;

	private Model3D m_Lower;

	private Model3D m_Weapon;

	private final static int MAX_TEXTURES = 20;
	
	private int[] m_Textures = new int[MAX_TEXTURES];	
	
	private FastVector strTextures = new FastVector();

	private byte[] fileContents;
	
	private int m_FilePointer = 0;


	public MD3Model() {
		m_Head = new Model3D();
		m_Upper = new Model3D();
		m_Lower = new Model3D();
		m_Weapon = new Model3D();
	}

	private final boolean IsInString(String strString, String strSubString) {
		int index = strString.indexOf(strSubString);

		if(index >= 0 && index < strString.length())
			return true;

		return false;
	}

	private final int byte2byte() {
		int b1 = (fileContents[m_FilePointer  ] & 0xFF);
		m_FilePointer += 1;
		return (b1);
	}

	private final short byte2short() {
		int s1 = (fileContents[m_FilePointer  ] & 0xFF);
		int s2 = (fileContents[m_FilePointer+1] & 0xFF) << 8;
		m_FilePointer += 2;
		return ((short)(s1 | s2));
	}

	private final int byte2int() {
		int i1 = (fileContents[m_FilePointer  ] & 0xFF);
		int i2 = (fileContents[m_FilePointer+1] & 0xFF) <<  8;
		int i3 = (fileContents[m_FilePointer+2] & 0xFF) << 16;
		int i4 = (fileContents[m_FilePointer+3] & 0xFF) << 24;
		m_FilePointer += 4;
		return (i1 | i2 | i3 | i4);
	}

	private final float byte2float() {
		return Float.intBitsToFloat(byte2int());
	}

	private final String byte2string(int size) {
		for(int i = m_FilePointer; i < m_FilePointer + size; i++)
		{
			if((fileContents[i] & 0xFF)== (byte)0)
				return new String(fileContents, m_FilePointer, i - m_FilePointer);
		}
		return new String(fileContents,m_FilePointer, size);
	}

	public final Model3D getModel(int whichPart) {
		if(whichPart == kLower) 
			return m_Lower;

		if(whichPart == kUpper) 
			return m_Upper;

		if(whichPart == kHead) 
			return m_Head;

		return m_Weapon;
	}

	public final void loadModel(String strPath, String strModel) {
		String strLowerModel;					
		String strUpperModel;					
		String strHeadModel;					
		String strLowerSkin;					
		String strUpperSkin;					
		String strHeadSkin;						

		strLowerModel = strPath + "/" + strModel + "_lower.md3";
		strUpperModel = strPath + "/" + strModel + "_upper.md3";
		strHeadModel = strPath + "/" + strModel + "_head.md3";
		
		strLowerSkin = strPath + "/" + strModel + "_lower.skin";
		strUpperSkin = strPath + "/" + strModel + "_upper.skin";
		strHeadSkin = strPath + "/" + strModel + "_head.skin";

		if(!importMD3(m_Head, strHeadModel))
		{
			System.out.println("[Error]: unable to load the HEAD part from model \"" + strModel + "\".");
			System.exit(0);
		}

		if(!importMD3(m_Upper, strUpperModel))		
		{
			System.out.println("[Error]: unable to load the UPPER part from model \"" + strModel + "\".");
			System.exit(0);
		}

		if(!importMD3(m_Lower, strLowerModel))
		{
			System.out.println("[Error]: unable to load the LOWER part from model \"" + strModel + "\".");
			System.exit(0);
		}

		if(!loadSkin(m_Lower, strLowerSkin))
		{
			System.out.println("[Error]: unable to load the LOWER part from model's skin \"" + strModel + "\".");
			System.exit(0);
		}

		if(!loadSkin(m_Upper, strUpperSkin))
		{
			System.out.println("[Error]: unable to load the UPPER part from model's skin \"" + strModel + "\".");
			System.exit(0);
		}

		if(!loadSkin(m_Head, strHeadSkin))
		{
			System.out.println("[Error]: unable to load the HEAD part from model's skin \"" + strModel + "\".");
			System.exit(0);
		}

		loadModelTextures(m_Lower, strPath);
		loadModelTextures(m_Upper, strPath);
		loadModelTextures(m_Head, strPath);

		String strConfigFile = strPath + "/" + strModel + "_animation.cfg";

		if(!loadAnimations(strConfigFile))
		{
			System.out.println("[Error]: unable to load the animation config file from model \"" + strModel + "\".");
			System.exit(0);
		}

		linkModel(m_Lower, m_Upper, "tag_torso");

		linkModel(m_Upper, m_Head, "tag_head");
	}

	public final void loadWeapon(String strPath, String strModel) {
		String strWeaponModel;					
		String strWeaponShader;					

		strWeaponModel = strPath + "/" + strModel + ".md3";

		if(!importMD3(m_Weapon, strWeaponModel))
		{
			System.out.println("[Error]: unable to load the weapon model \"" + strModel + "\".");
			System.exit(0);
		}

		strWeaponShader = strPath + "/" + strModel + ".shader";

		if(!loadShader(m_Weapon, strWeaponShader))
		{
			System.out.println("[Error]: unable to load the shader for weapon \"" + strModel + "\".");
			System.exit(0);
		}

		loadModelTextures(m_Weapon, strPath);

		linkModel(m_Upper, m_Weapon, "tag_weapon");
	}

	private final void loadModelTextures(Model3D pModel, String strPath) {
		for(int i = 0; i < pModel.numOfMaterials; i++)
		{
			if(((MaterialInfo)pModel.pMaterials.elementAt(i)).strFile != null)
			{
				boolean bNewTexture = true;

				for(int j = 0; j < strTextures.size(); j++)
				{
					if(((MaterialInfo)pModel.pMaterials.elementAt(i)).strFile.equals(((String)strTextures.elementAt(j))))
					{
						bNewTexture = false;

						((MaterialInfo)pModel.pMaterials.elementAt(i)).texureId = j;
					}
				}

				if(bNewTexture == false)
					continue;
				
				String strFullPath;

				strFullPath = strPath + "/" + ((MaterialInfo)pModel.pMaterials.elementAt(i)).strFile;

				createTexture(m_Textures, strFullPath, strTextures.size());								

				((MaterialInfo)pModel.pMaterials.elementAt(i)).texureId = strTextures.size();

				strTextures.addElement(((MaterialInfo)pModel.pMaterials.elementAt(i)).strFile);
			}
		}
	}

	private final boolean loadAnimations(String strConfigFile) {
		try
		{
			AnimationInfo[] animations = new AnimationInfo[MAX_ANIMATIONS];
			BufferedReader reader = new BufferedReader(new FileReader(strConfigFile));
			String strWord = "";					
			String strLine = "";					
			int currentAnim = 0;					
			int torsoOffset = 0;					
			StringTokenizer tokenizer;

			while((strLine = reader.readLine()) != null)
			{
				if(strLine.length() == 0)
				{
					continue;
				}
				if(!Character.isDigit(strLine.charAt(0)))
				{
					continue;
				}

				tokenizer = new StringTokenizer(strLine);

				int startFrame		= Integer.parseInt(tokenizer.nextToken());
				int numOfFrames		= Integer.parseInt(tokenizer.nextToken());
				int loopingFrames	= Integer.parseInt(tokenizer.nextToken());
				int framesPerSecond = Integer.parseInt(tokenizer.nextToken());
				
				animations[currentAnim] = new AnimationInfo();
				animations[currentAnim].startFrame		= startFrame;
				animations[currentAnim].endFrame		= startFrame + numOfFrames;
				animations[currentAnim].loopingFrames	= loopingFrames;
				animations[currentAnim].framesPerSecond = framesPerSecond;

				tokenizer.nextToken();

				animations[currentAnim].strName = tokenizer.nextToken();

				if(IsInString(strLine, "BOTH"))
				{
					m_Upper.pAnimations.addElement(animations[currentAnim]);
					m_Lower.pAnimations.addElement(animations[currentAnim]);
				}
				else if(IsInString(strLine, "TORSO"))
				{
					m_Upper.pAnimations.addElement(animations[currentAnim]);
				}
				else if(IsInString(strLine, "LEGS"))
				{
					if(torsoOffset == 0)
						torsoOffset = animations[LEGS_WALKCR].startFrame - animations[TORSO_GESTURE].startFrame;

					animations[currentAnim].startFrame -= torsoOffset;
					animations[currentAnim].endFrame -= torsoOffset;

					m_Lower.pAnimations.addElement(animations[currentAnim]);
				}
				currentAnim++;
			}	

			m_Lower.numOfAnimations = m_Lower.pAnimations.size();
			m_Upper.numOfAnimations = m_Upper.pAnimations.size();
			m_Head.numOfAnimations = m_Head.pAnimations.size();
			m_Weapon.numOfAnimations = m_Head.pAnimations.size();
		}
		catch(Exception e)
		{
			return false;
		}
		return true;
	}

	private final void linkModel(Model3D pModel, Model3D pLink, String strTagName) {
		for(int i = 0; i < pModel.numOfTags; i++)
		{
			if(pModel.pTags[i].strName.equals(strTagName))
			{
				pModel.pLinks[i] = pLink;
				return;
			}
		}
	}

	private final void updateModel(Model3D pModel) {
		int startFrame = 0;
		int endFrame   = 1;

		AnimationInfo pAnim = (AnimationInfo)pModel.pAnimations.elementAt(pModel.currentAnim);

		if(pModel.numOfAnimations != 0)
		{
			startFrame = pAnim.startFrame;
			endFrame   = pAnim.endFrame;
		}


		pModel.nextFrame = pModel.currentFrame + 1;
		if (pModel.nextFrame >= endFrame)
			pModel.nextFrame = startFrame;


		setCurrentTime(pModel);
	}

	public final void draw(Matrix4f modelMatrix, SimpleShader shader) {
		Matrix4f rootMatrix = new Matrix4f(modelMatrix).rotateX((float)Math.toRadians(-90.0f));
		updateModel(m_Lower);
		updateModel(m_Upper);
		drawLink(m_Lower, rootMatrix, shader);
	}

	private final void drawLink(Model3D pModel, Matrix4f currentMatrix, SimpleShader shader) {
		renderModel(pModel, currentMatrix, shader);
		Quaternion qQuat = new Quaternion();
		Quaternion qNextQuat = new Quaternion();
		Quaternion qInterpolatedQuat;
		float[] pMatrix;
		float[] pNextMatrix;
		float[] finalMatrix = new float[16];

		for(int i = 0; i < pModel.numOfTags; i++)
		{
			Model3D pLink = pModel.pLinks[i];
			if(pLink != null)
			{
				Vector3f vOldPosition = pModel.pTags[pModel.currentFrame * pModel.numOfTags + i].vPosition;
				Vector3f vNextPosition = pModel.pTags[pModel.nextFrame * pModel.numOfTags + i].vPosition;

				Vector3f vPosition = new Vector3f();
				vPosition.x = vOldPosition.x + pModel.t * (vNextPosition.x - vOldPosition.x);
				vPosition.y = vOldPosition.y + pModel.t * (vNextPosition.y - vOldPosition.y);
				vPosition.z = vOldPosition.z + pModel.t * (vNextPosition.z - vOldPosition.z);

				pMatrix = pModel.pTags[pModel.currentFrame * pModel.numOfTags + i].rotation;
				pNextMatrix = pModel.pTags[pModel.nextFrame * pModel.numOfTags + i].rotation;

				qQuat.createFromMatrix(pMatrix, 3);
				qNextQuat.createFromMatrix(pNextMatrix, 3);
				qInterpolatedQuat = qQuat.slerp(qQuat, qNextQuat, pModel.t);

				qInterpolatedQuat.createMatrix(finalMatrix);
				finalMatrix[12] = vPosition.x;
				finalMatrix[13] = vPosition.y;
				finalMatrix[14] = vPosition.z;


				Matrix4f tagMatrix = new Matrix4f(
						finalMatrix[0], finalMatrix[4], finalMatrix[8],  0f,
						finalMatrix[1], finalMatrix[5], finalMatrix[9],  0f,
						finalMatrix[2], finalMatrix[6], finalMatrix[10], 0f,
						vPosition.x,   vPosition.y,    vPosition.z,     1f
				);

				Matrix4f childMatrix = new Matrix4f(currentMatrix).mul(tagMatrix);

				drawLink(pLink, childMatrix, shader);
			}
		}
	}

	private final void setCurrentTime(Model3D pModel) {
		if(pModel.pAnimations.size() == 0)
			return;

		long time = System.currentTimeMillis();
		
		long elapsedTime = time - pModel.lastTime;

		int animationSpeed = ((AnimationInfo)pModel.pAnimations.elementAt(pModel.currentAnim)).framesPerSecond;

		float t = elapsedTime / (1000f / animationSpeed);

		if(elapsedTime >= (1000.0f / animationSpeed))
		{
			pModel.currentFrame = pModel.nextFrame;

			pModel.lastTime = time;
		}

		pModel.t = t;
	}

	private final void renderModel(Model3D pModel) {
		throw new UnsupportedOperationException("Use draw(Matrix4f, SimpleShader) para renderizar con OpenGL moderno.");
	}

	private final void renderModel(Model3D pModel, Matrix4f modelMatrix, SimpleShader shader) {

		if(pModel.pObject == null)
			return;

		shader.loadModelMatrix(modelMatrix);

		for(int i = 0; i < pModel.numOfObjects; i++) {
			Object3D pObject = (Object3D)pModel.pObject.elementAt(i);
			int currentIndex = pModel.currentFrame * pObject.numOfVerts;
			int nextIndex = pModel.nextFrame * pObject.numOfVerts;
			int textureID = ((MaterialInfo)pModel.pMaterials.elementAt(pObject.materialID)).texureId;

			GL13.glActiveTexture(GL13.GL_TEXTURE0);
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, m_Textures[textureID]);

			drawObjectWithVbo(pModel, pObject, currentIndex, nextIndex);
		}
	}


	private void drawObjectWithVbo(Model3D pModel, Object3D pObject, int currentIndex, int nextIndex) {
		int vertexCount = pObject.numOfFaces * 3;

		FloatBuffer verticesBuffer  = BufferUtils.createFloatBuffer(vertexCount * 3);
		FloatBuffer texCoordsBuffer = BufferUtils.createFloatBuffer(vertexCount * 2);

		for (int j = 0; j < pObject.numOfFaces; j++) {
			for (int whichVertex = 0; whichVertex < 3; whichVertex++) {
				int index = pObject.pFaces[j].vertIndex[whichVertex];
				Vector3f vPoint1 = pObject.pVerts[currentIndex + index];
				Vector3f vPoint2 = pObject.pVerts[nextIndex + index];

				verticesBuffer.put(vPoint1.x + pModel.t * (vPoint2.x - vPoint1.x));
				verticesBuffer.put(vPoint1.y + pModel.t * (vPoint2.y - vPoint1.y));
				verticesBuffer.put(vPoint1.z + pModel.t * (vPoint2.z - vPoint1.z));

				if (pObject.pTexVerts != null) {
					texCoordsBuffer.put(pObject.pTexVerts[index].x);
					texCoordsBuffer.put(pObject.pTexVerts[index].y);
				} else {
					texCoordsBuffer.put(0.0f);
					texCoordsBuffer.put(0.0f);
				}
			}
		}
		verticesBuffer.flip();
		texCoordsBuffer.flip();

		if (pObject.vaoID == -1) {
			pObject.vaoID         = GL30.glGenVertexArrays();
			pObject.verticesVboID  = GL15.glGenBuffers();
			pObject.texCoordsVboID = GL15.glGenBuffers();

			GL30.glBindVertexArray(pObject.vaoID);

			GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, pObject.verticesVboID);
			GL15.glBufferData(GL15.GL_ARRAY_BUFFER, verticesBuffer, GL15.GL_STREAM_DRAW);
			GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 0, 0L);
			GL20.glEnableVertexAttribArray(0);

			GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, pObject.texCoordsVboID);
			GL15.glBufferData(GL15.GL_ARRAY_BUFFER, texCoordsBuffer, GL15.GL_STREAM_DRAW);
			GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, 0, 0L);
			GL20.glEnableVertexAttribArray(1);

			GL30.glBindVertexArray(0);
		} else {
			GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, pObject.verticesVboID);
			GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, verticesBuffer);
			GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
		}

		GL30.glBindVertexArray(pObject.vaoID);
		GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, vertexCount);
		GL30.glBindVertexArray(0);
	}

	public final void setTorsoAnimation(String strAnimation)
	{
		for(int i = 0; i < m_Upper.numOfAnimations; i++)
		{
			if(((AnimationInfo)m_Upper.pAnimations.elementAt(i)).strName.equals(strAnimation))
			{
				m_Upper.currentAnim = i;
				m_Upper.currentFrame = ((AnimationInfo)m_Upper.pAnimations.elementAt(m_Upper.currentAnim)).startFrame;
				return;
			}
		}
	}

	public final void setLegsAnimation(String strAnimation)
	{
		for(int i = 0; i < m_Lower.numOfAnimations; i++)
		{
			if(((AnimationInfo)m_Lower.pAnimations.elementAt(i)).strName.equals(strAnimation))
			{
				m_Lower.currentAnim = i;
				m_Lower.currentFrame = ((AnimationInfo)m_Lower.pAnimations.elementAt(m_Lower.currentAnim)).startFrame;
				return;
			}
		}
	}

	private final boolean importMD3(Model3D pModel, String file)
	{
		try
		{
			File f = new File(file);

			BufferedInputStream bis = new BufferedInputStream(new FileInputStream(f));

			fileContents = new byte[(int)f.length()];

			bis.read(fileContents, 0, (int)f.length());

			bis.close();

			m_FilePointer = 0;

			m_Header = new MD3Header();

			String ID = m_Header.fileID;

			if(!ID.equals("IDP3") || m_Header.version != 15)
			{
				System.out.println("[Error]: file " + file + " version is not valid.");
				System.exit(0);
			}
			
			readMD3Data(pModel);
		}
		catch(Exception e)
		{
			System.out.println("[Error]: can't read " + file + " correctly.");
			System.exit(0);
		}
		return true;
	}

	private final void readMD3Data(Model3D pModel)
	{
		int i = 0;
		m_pBones = new MD3Bone[m_Header.numFrames];
		for (i = 0; i < m_Header.numFrames ; i++)
			m_pBones[i] = new MD3Bone();

		m_pBones = null;

		pModel.pTags = new MD3Tag[m_Header.numFrames * m_Header.numTags];

		for(i = 0; i < m_Header.numFrames * m_Header.numTags; i++)
			pModel.pTags[i] = new MD3Tag();

		pModel.numOfTags = m_Header.numTags;
		
		pModel.pLinks = new Model3D[m_Header.numTags];
		
		for(i = 0; i < m_Header.numTags; i++)
			pModel.pLinks[i] = null;

		int meshOffset = m_FilePointer;

		MD3MeshInfo meshHeader = new MD3MeshInfo();

		for (int j = 0; j < m_Header.numMeshes; j++)
		{
			m_FilePointer = meshOffset;
			meshHeader = new MD3MeshInfo();

			m_pSkins     = new MD3Skin[meshHeader.numSkins];
			m_pTexCoords = new MD3TexCoord[meshHeader.numVertices];
			m_pTriangles = new MD3Face[meshHeader.numTriangles];
			m_pVertices  = new MD3Triangle[meshHeader.numVertices * meshHeader.numMeshFrames];

			for (i = 0; i < meshHeader.numSkins ; i++)
				m_pSkins[i] = new MD3Skin();
			
			m_FilePointer = meshOffset + meshHeader.triStart;

			for (i = 0; i < meshHeader.numTriangles; i++)
				m_pTriangles[i] = new MD3Face();

			m_FilePointer = meshOffset + meshHeader.uvStart;

			for (i = 0; i < meshHeader.numVertices; i++)
				m_pTexCoords[i] = new MD3TexCoord();

			m_FilePointer = meshOffset + meshHeader.vertexStart;
			for(i = 0; i < meshHeader.numMeshFrames * meshHeader.numVertices; i++)
				m_pVertices[i] = new MD3Triangle();

			convertDataStructures(pModel, meshHeader);

			m_pSkins = null;    
			m_pTexCoords = null;
			m_pTriangles = null;
			m_pVertices = null;   

			meshOffset += meshHeader.meshSize;
		}
	}

	private final void convertDataStructures(Model3D pModel, MD3MeshInfo meshHeader)
	{
		int i = 0;
		pModel.numOfObjects++;
		Object3D currentMesh = new Object3D();
		currentMesh.strName = meshHeader.strName;
		currentMesh.numOfVerts   = meshHeader.numVertices;
		currentMesh.numTexVertex = meshHeader.numVertices;
		currentMesh.numOfFaces   = meshHeader.numTriangles;
		currentMesh.pVerts    = new Vector3f[currentMesh.numOfVerts * meshHeader.numMeshFrames];
		currentMesh.pTexVerts = new Vector2f[currentMesh.numOfVerts];
		currentMesh.pFaces    = new Face[currentMesh.numOfFaces];
		for(i = 0; i < currentMesh.numOfVerts * meshHeader.numMeshFrames; i++)
		{
			currentMesh.pVerts[i] = new Vector3f();
			currentMesh.pVerts[i].x =  m_pVertices[i].vertex[0] / 64.0f;
			currentMesh.pVerts[i].y =  m_pVertices[i].vertex[1] / 64.0f;
			currentMesh.pVerts[i].z =  m_pVertices[i].vertex[2] / 64.0f;
		}
		for(i = 0; i < currentMesh.numTexVertex; i++)
		{
			currentMesh.pTexVerts[i] = new Vector2f();
			currentMesh.pTexVerts[i].x =  m_pTexCoords[i].textureCoord[0];
			currentMesh.pTexVerts[i].y = -m_pTexCoords[i].textureCoord[1];
		}
		for(i = 0; i < currentMesh.numOfFaces; i++)
		{
			currentMesh.pFaces[i] = new Face();
			currentMesh.pFaces[i].vertIndex[0] = m_pTriangles[i].vertexIndices[0];
			currentMesh.pFaces[i].vertIndex[1] = m_pTriangles[i].vertexIndices[1];
			currentMesh.pFaces[i].vertIndex[2] = m_pTriangles[i].vertexIndices[2];
			currentMesh.pFaces[i].coordIndex[0] = m_pTriangles[i].vertexIndices[0];
			currentMesh.pFaces[i].coordIndex[1] = m_pTriangles[i].vertexIndices[1];
			currentMesh.pFaces[i].coordIndex[2] = m_pTriangles[i].vertexIndices[2];
		}
		pModel.pObject.addElement(currentMesh);
	}

	private final boolean loadSkin(Model3D pModel, String strSkin)
	{
		try
		{
			BufferedReader reader = new BufferedReader(new FileReader(strSkin));
			String strLine;
			int textureNameStart = 0;
			while((strLine = reader.readLine()) != null)
			{
				for(int i = 0; i < pModel.numOfObjects; i++)
				{
					if(IsInString(strLine, ((Object3D)pModel.pObject.elementAt(i)).strName))			
					{			
						textureNameStart = strLine.lastIndexOf("/") + 1;

						MaterialInfo texture = new MaterialInfo();

						texture.strFile = strLine.substring(textureNameStart);
						
						texture.uTile = texture.uTile = 1;

						((Object3D)pModel.pObject.elementAt(i)).materialID = pModel.numOfMaterials;
						((Object3D)pModel.pObject.elementAt(i)).bHasTexture = true;

						pModel.numOfMaterials++;

						pModel.pMaterials.addElement(texture);
					}
				}
			}
			reader.close();
		}
		catch(Exception e)
		{
			return false;
		}
		return true;
	}

	private final boolean loadShader(Model3D pModel, String strShader)
	{
		try
		{
			BufferedReader reader = new BufferedReader(new FileReader(strShader));

			String strLine;
			int currentIndex = 0;
			
			while((strLine = reader.readLine()) != null)
			{
				MaterialInfo texture = new MaterialInfo();

				texture.strFile = strLine;
						
				texture.uTile = texture.uTile = 1;

				((Object3D)pModel.pObject.elementAt(currentIndex)).materialID = pModel.numOfMaterials;
				((Object3D)pModel.pObject.elementAt(currentIndex)).bHasTexture = true;

				pModel.numOfMaterials++;

				pModel.pMaterials.addElement(texture);

				currentIndex++;
			}

			reader.close();
		}
		catch(Exception e)
		{
			return false;
		}

		return true;
	}

	private final void createTexture(int textureArray[], String strFileName, int textureID)
	{
		textureArray[textureID] = loadTexture(strFileName);
	}

	public int loadTexture(String path)
	{
		Image image = (new javax.swing.ImageIcon(path)).getImage();
		
		BufferedImage tex = new BufferedImage(image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_3BYTE_BGR);
		Graphics2D g = (Graphics2D)tex.getGraphics();
		g.drawImage(image, null, null);
		g.dispose();


		AffineTransform tx = AffineTransform.getScaleInstance(1, -1);
		tx.translate(0, -image.getHeight(null));
		AffineTransformOp op = new AffineTransformOp(tx, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
		tex = op.filter(tex, null);

		byte data[] = (byte[])tex.getRaster().getDataElements(0, 0, tex.getWidth(), tex.getHeight(), null);
		ByteBuffer buffer2 = BufferUtils.createByteBuffer(data.length);
		buffer2.put(data);
		buffer2.rewind();
		IntBuffer buffer = BufferUtils.createIntBuffer(1);
		GL11.glGenTextures(buffer);
		GL11.glBindTexture(  GL11.GL_TEXTURE_2D, buffer.get(0));

		GL11.glTexParameteri(  GL11.GL_TEXTURE_2D,   GL11.GL_TEXTURE_MIN_FILTER,   GL11.GL_LINEAR);
		GL11.glTexParameteri(  GL11.GL_TEXTURE_2D,   GL11.GL_TEXTURE_MAG_FILTER,   GL11.GL_LINEAR);

		GL11.glTexImage2D(  GL11.GL_TEXTURE_2D, 0,   GL11.GL_RGB, tex.getWidth(), tex.getHeight(), 0,   GL11.GL_RGB,   GL11.GL_UNSIGNED_BYTE, buffer2);
		buffer2.clear();
		return buffer.get(0);
	}

	public void drawMD3(SimpleShader shader, MasterRenderer masterRenderer, Camera camera, Matrix4f modelMatrix) {
		GL11.glFrontFace(GL11.GL_CW); 
		shader.start();
		shader.loadProjectionMatrix(masterRenderer.getProjectionMatrix());
		shader.loadViewMatrix(Maths.createViewMatrix(camera));
		this.draw(modelMatrix, shader);
		shader.stop();
		GL11.glFrontFace(GL11.GL_CCW); 
	}

}