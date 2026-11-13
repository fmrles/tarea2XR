package org.lwjglb.md3;




public final class Quaternion
{

	public float x;

	public float y;

	public float z;

	public float w;




	public Quaternion()
	{
		x = 0.0f;
		y = 0.0f;
		z = 0.0f;
		w = 1.0f;
	}




	public Quaternion(float X, float Y, float Z, float W) 
	{ 
		x = X;
		y = Y;
		z = Z;
		w = W;
	}



	
	public final void createMatrix(float pMatrix[])
	{
		if(pMatrix == null)
			return;
			
		pMatrix[ 0] = 1.0f - 2.0f * ( y * y + z * z );  
		pMatrix[ 1] = 2.0f * ( x * y - w * z );  
		pMatrix[ 2] = 2.0f * ( x * z + w * y );  
		pMatrix[ 3] = 0.0f;  

		pMatrix[ 4] = 2.0f * ( x * y + w * z );  
		pMatrix[ 5] = 1.0f - 2.0f * ( x * x + z * z );  
		pMatrix[ 6] = 2.0f * ( y * z - w * x );  
		pMatrix[ 7] = 0.0f;  

		pMatrix[ 8] = 2.0f * ( x * z - w * y );  
		pMatrix[ 9] = 2.0f * ( y * z + w * x );  
		pMatrix[10] = 1.0f - 2.0f * ( x * x + y * y );  
		pMatrix[11] = 0.0f;  

		pMatrix[12] = 0;  
		pMatrix[13] = 0;  
		pMatrix[14] = 0;  
		pMatrix[15] = 1.0f;
	}


	

	public final void createFromMatrix(float[] pTheMatrix, int rowColumnCount)
	{
		if((pTheMatrix == null) || ((rowColumnCount != 3) && (rowColumnCount != 4)))
			return;

		float pMatrix[] = pTheMatrix;

		float m4x4[] = new float[16];

		if(rowColumnCount == 3)
		{
			m4x4[0]  = pTheMatrix[0];	m4x4[1]  = pTheMatrix[1];	m4x4[2]  = pTheMatrix[2];	m4x4[ 3] = 0f;
			m4x4[4]  = pTheMatrix[3];	m4x4[5]  = pTheMatrix[4];	m4x4[6]  = pTheMatrix[5];	m4x4[ 7] = 0f;
			m4x4[8]  = pTheMatrix[6];	m4x4[9]  = pTheMatrix[7];	m4x4[10] = pTheMatrix[8];	m4x4[11] = 0f;

			m4x4[12] = 0f;	m4x4[13] = 0f;	m4x4[14] = 0f;	m4x4[15] = 1;

			pMatrix = m4x4;
		}

		float diagonal = pMatrix[0] + pMatrix[5] + pMatrix[10] + 1;
		float scale = 0.0f;

		if(diagonal > 0.00000001f)
		{
			scale = (float)Math.sqrt(diagonal) * 2f;

			x = (pMatrix[9] - pMatrix[6]) / scale;
			y = (pMatrix[2] - pMatrix[8]) / scale;
			z = (pMatrix[4] - pMatrix[1]) / scale;
			w = 0.25f * scale;
		}
		else 
		{
			if(pMatrix[0] > pMatrix[5] && pMatrix[0] > pMatrix[10])  
			{	
				scale  = (float)Math.sqrt(1.0f + pMatrix[0] - pMatrix[5] - pMatrix[10]) * 2.0f;

				x = 0.25f * scale;
				y = (pMatrix[4] + pMatrix[1]) / scale;
				z = (pMatrix[2] + pMatrix[8]) / scale;
				w = (pMatrix[9] - pMatrix[6]) / scale;	
			} 
			else if(pMatrix[5] > pMatrix[10]) 
			{
				scale  = (float)Math.sqrt(1.0f + pMatrix[5] - pMatrix[0] - pMatrix[10]) * 2.0f;
					
				x = (pMatrix[4] + pMatrix[1]) / scale;
				y = 0.25f * scale;
				z = (pMatrix[9] + pMatrix[6]) / scale;
				w = (pMatrix[2] - pMatrix[8]) / scale;
			} 
			else 
			{	
				scale  = (float)Math.sqrt(1.0f + pMatrix[10] - pMatrix[0] - pMatrix[5]) * 2.0f;

				x = (pMatrix[2] + pMatrix[8]) / scale;
				y = (pMatrix[9] + pMatrix[6]) / scale;
				z = 0.25f * scale;
				w = (pMatrix[4] - pMatrix[1]) / scale;
			}
		}
	}


	

	public final Quaternion slerp(Quaternion q1, Quaternion q2, float t)
	{
		Quaternion qInterpolated = new Quaternion();

		if(q1.x == q2.x && q1.y == q2.y && q1.z == q2.z && q1.w == q2.w) 
			return q1;

		float result = (q1.x * q2.x) + (q1.y * q2.y) + (q1.z * q2.z) + (q1.w * q2.w);

		if(result < 0.0f)
		{
			q2.x = -q2.x;
			q2.y = -q2.y;
			q2.z = -q2.z;
			q2.w = -q2.w;
			result = -result;
		}

		float scale0 = 1 - t;
		float scale1 = t;

		if((1 - result) > 0.1f)
		{
			float theta = (float)Math.acos(result);
			float sinTheta = (float)Math.sin(theta);

			scale0 = (float)Math.sin((1 - t) * theta) / sinTheta;
			scale1 = (float)Math.sin((t * theta)) / sinTheta;
		}	

		qInterpolated.x = (scale0 * q1.x) + (scale1 * q2.x);
		qInterpolated.y = (scale0 * q1.y) + (scale1 * q2.y);
		qInterpolated.z = (scale0 * q1.z) + (scale1 * q2.z);
		qInterpolated.w = (scale0 * q1.w) + (scale1 * q2.w);

		return qInterpolated;
	}
}