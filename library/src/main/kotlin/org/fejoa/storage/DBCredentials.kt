package org.fejoa.storage

import org.fejoa.crypto.SignCredentials
import org.fejoa.crypto.SymCredentials


class SignCredentialsDBValue(parent: DBObject, relativePath: String) : DBValue<SignCredentials>(parent, relativePath) {
    suspend override fun write(obj: SignCredentials) {
        dir.writeString(path, obj.toJson())
    }

    suspend override fun get(): SignCredentials {
        return signCredentialsFromJson(dir.readString(path))
    }
}

class SignCredentialList(dir: IOStorageDir, path: String) : DBMap<HashValue, SignCredentialsDBValue>(dir, path) {
    suspend override fun list(): Collection<String> {
        return dir.listFiles(path)
    }

    override fun get(key: HashValue): SignCredentialsDBValue {
        return SignCredentialsDBValue(this, key.toHex())
    }
}

class SymCredentialsDBValue(parent: DBObject, relativePath: String) : DBValue<SymCredentials>(parent, relativePath) {
    suspend override fun write(obj: SymCredentials) {
        dir.writeString(path, obj.toJson())
    }

    suspend override fun get(): SymCredentials {
        return symCredentialsFromJson(dir.readString(path))
    }
}


class SymCredentialList(dir: IOStorageDir, path: String) : DBMap<HashValue, SymCredentialsDBValue>(dir, path) {
    suspend override fun list(): Collection<String> {
        return dir.listFiles(path)
    }

    override fun get(key: HashValue): SymCredentialsDBValue {
        return SymCredentialsDBValue(this, key.toHex())
    }
}
