package org.lwjglb.md3;

public final class FastVector {

	private Object m_v[];  
	private int m_ptr = 0;  

	public FastVector()	{}


	public final int size()	{
		return m_ptr;
	}

	public final void addElement(Object val) {
		if(m_v == null)
			m_v = new Object[3];
		if(m_ptr >= m_v.length) {
			Object[] nv = new Object[m_v.length * 2];
			System.arraycopy(m_v, 0, nv, 0, m_v.length);
			m_v = nv;
		}
		m_v[m_ptr++] = val;
	}

	public final Object elementAt(int i) {
		return m_v[i];
	}

	public final void removeElementAt(int i) {
		m_v[i] = m_v[--m_ptr];
	}
}