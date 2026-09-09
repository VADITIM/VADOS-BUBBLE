/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: C:\Users\vn\tools\android-sdk\build-tools\35.0.0\aidl.exe -pC:\Users\vn\tools\android-sdk\platforms\android-36\framework.aidl -oC:\Users\vn\source\VADOS-BUBBLE\app\build\generated\aidl_source_output_dir\debug\out -IC:\Users\vn\source\VADOS-BUBBLE\app\src\main\aidl -IC:\Users\vn\source\VADOS-BUBBLE\app\src\debug\aidl -dC:\Users\vn\AppData\Local\Temp\aidl6509690844357247832.d C:\Users\vn\source\VADOS-BUBBLE\app\src\main\aidl\com\v\island\IShellService.aidl
 */
package com.v.island;
public interface IShellService extends android.os.IInterface
{
  /** Default implementation for IShellService. */
  public static class Default implements com.v.island.IShellService
  {
    @Override public java.lang.String execute(java.lang.String command) throws android.os.RemoteException
    {
      return null;
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements com.v.island.IShellService
  {
    /** Construct the stub at attach it to the interface. */
    @SuppressWarnings("this-escape")
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an com.v.island.IShellService interface,
     * generating a proxy if needed.
     */
    public static com.v.island.IShellService asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof com.v.island.IShellService))) {
        return ((com.v.island.IShellService)iin);
      }
      return new com.v.island.IShellService.Stub.Proxy(obj);
    }
    @Override public android.os.IBinder asBinder()
    {
      return this;
    }
    @Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
    {
      java.lang.String descriptor = DESCRIPTOR;
      if (code >= android.os.IBinder.FIRST_CALL_TRANSACTION && code <= android.os.IBinder.LAST_CALL_TRANSACTION) {
        data.enforceInterface(descriptor);
      }
      if (code == INTERFACE_TRANSACTION) {
        reply.writeString(descriptor);
        return true;
      }
      switch (code)
      {
        case TRANSACTION_execute:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          java.lang.String _result = this.execute(_arg0);
          reply.writeNoException();
          reply.writeString(_result);
          break;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
      return true;
    }
    private static class Proxy implements com.v.island.IShellService
    {
      private android.os.IBinder mRemote;
      Proxy(android.os.IBinder remote)
      {
        mRemote = remote;
      }
      @Override public android.os.IBinder asBinder()
      {
        return mRemote;
      }
      public java.lang.String getInterfaceDescriptor()
      {
        return DESCRIPTOR;
      }
      @Override public java.lang.String execute(java.lang.String command) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.lang.String _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(command);
          boolean _status = mRemote.transact(Stub.TRANSACTION_execute, _data, _reply, 0);
          _reply.readException();
          _result = _reply.readString();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
    }
    static final int TRANSACTION_execute = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
  }
  /** @hide */
  public static final java.lang.String DESCRIPTOR = "com.v.island.IShellService";
  public java.lang.String execute(java.lang.String command) throws android.os.RemoteException;
}
