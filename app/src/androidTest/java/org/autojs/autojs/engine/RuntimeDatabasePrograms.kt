package org.autojs.autojs.engine

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

internal object RuntimeDatabasePrograms {
    fun exercise(name: String) = """
        const db=await database.open({name:${JSONObject.quote(name)},timeoutMs:10000});
        try {
          eq(Object.isFrozen(db),true,'Frozen handle');
          await db.exec('DROP TABLE IF EXISTS items');await db.exec('CREATE TABLE items(id INTEGER PRIMARY KEY, name TEXT UNIQUE, score REAL)');
          const typed=await db.get('SELECT typeof(?) AS nil, typeof(?) AS integerType, typeof(?) AS realType, typeof(?) AS booleanType, typeof(?) AS textType, ? AS nullValue',[null,42,1.25,true,'42',null]);
          eq(typed,{nil:'null',integerType:'integer',realType:'real',booleanType:'integer',textType:'text',nullValue:null},'Typed query binding');
          eq(await db.get('SELECT X\'00FF\' AS bytes'),{bytes:{base64:'AP8='}},'Blob result');
          const inserted=await db.run('/* owned fixture */ INSERT INTO items(name,score) VALUES(?,?),(?,?)',['alpha',1,'\u4e2d\u6587\ud83d\ude00',2]);
          eq(inserted,{rowsAffected:2,insertId:2},'Multirow insert count');
          eq(await db.run('INSERT OR IGNORE INTO items(name,score) VALUES(?,?)',['alpha',10]),{rowsAffected:0,insertId:null},'Ignored insert');
          await rejects(()=>db.run('INSERT OR FAIL INTO items(name,score) VALUES(?,?),(?,?)',['partial',5,'alpha',6]));
          eq(await db.get('SELECT COUNT(*) AS n FROM items WHERE name=?',['partial']),{n:1},'OR FAIL preserves earlier rows');
          await db.run('DELETE FROM items WHERE name=?',['partial']);
          await rejects(()=>db.run('INSERT OR ROLLBACK INTO items(name,score) VALUES(?,?),(?,?)',['statement-rollback',5,'alpha',6]));
          eq(await db.get('SELECT COUNT(*) AS n FROM items WHERE name=?',['statement-rollback']),{n:0},'OR ROLLBACK undoes earlier rows');
          eq(await db.get('SELECT name FROM items WHERE id=?',[99]),null,'Missing row');
          const rows=await db.all('SELECT name,score FROM items ORDER BY id');eq(rows,[{name:'alpha',score:1},{name:'\u4e2d\u6587\ud83d\ude00',score:2}],'Rows');eq(Object.isFrozen(rows),true,'Frozen row array');
          await rejects(()=>db.transaction([{type:'run',sql:'INSERT INTO items(name,score) VALUES(?,?)',params:['rolled-back',3]},{type:'run',sql:'INSERT INTO items(name,score) VALUES(?,?)',params:['alpha',4]}]));
          eq(await db.get('SELECT COUNT(*) AS n FROM items WHERE name=?',['rolled-back']),{n:0},'Transaction rollback');
          const tx=await db.transaction([{type:'exec',sql:'UPDATE items SET score=? WHERE name=?',params:[3,'alpha']},{type:'run',sql:'INSERT INTO items(name,score) VALUES(?,?)',params:['gamma',4]},{type:'get',sql:'SELECT score FROM items WHERE name=?',params:['alpha']},{type:'all',sql:'SELECT id FROM items ORDER BY id'}]);
          eq(tx[0],null,'Transaction exec result');eq(tx[1],{rowsAffected:1,insertId:3},'Transaction run');eq(tx[2],{score:3},'Transaction get');eq(tx[3],[{id:1},{id:2},{id:3}],'Transaction all');
          await rejects(()=>database.open({}));await rejects(()=>db.run('SELECT ?',[NaN]));await rejects(()=>db.transaction(()=>{}));
          await rejects(()=>db.exec('ATTACH DATABASE "outside" AS other'));await rejects(()=>database.open('../outside'));
          const abort=new AbortController();abort.abort(new Error('owned abort'));await rejects(()=>sqlite({name:'aborted',signal:abort.signal}));
        }finally{eq(await db.close(),true,'Close');}
        eq(await db.close(),false,'Repeated close');await rejects(()=>db.get('SELECT 1'));
        const alias=await sqlite(${JSONObject.quote(name)});eq(await alias.get('SELECT COUNT(*) AS n FROM items'),{n:3},'Callable alias persistence');await alias.close();
        return {database:true,typedBindings:true,multirowAffected:2,rollback:true,conflictPolicies:true};
    """.trimIndent()

    fun cleanup(context: Context, workingDirectory: File) {
        val hash=MessageDigest.getInstance("SHA-256").digest(workingDirectory.path.toByteArray()).joinToString(""){"%02x".format(it)}.take(16)
        val base=File(context.filesDir,"node-databases/${context.packageName}").canonicalFile
        val owned=File(base,hash).canonicalFile;check(owned.parentFile==base);owned.deleteRecursively()
    }
}
