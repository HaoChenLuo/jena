/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hp.hpl.jena.tdb.setup;

import java.util.HashMap ;
import java.util.Map ;

import org.openjena.atlas.lib.ColumnMap ;
import org.openjena.atlas.lib.StrUtils ;
import org.slf4j.Logger ;

import com.hp.hpl.jena.query.ARQ ;
import com.hp.hpl.jena.sparql.engine.optimizer.reorder.ReorderLib ;
import com.hp.hpl.jena.sparql.engine.optimizer.reorder.ReorderTransformation ;
import com.hp.hpl.jena.sparql.sse.SSEParseException ;
import com.hp.hpl.jena.tdb.TDB ;
import com.hp.hpl.jena.tdb.TDBException ;
import com.hp.hpl.jena.tdb.base.block.BlockMgr ;
import com.hp.hpl.jena.tdb.base.file.BufferChannel ;
import com.hp.hpl.jena.tdb.base.file.FileSet ;
import com.hp.hpl.jena.tdb.base.file.Location ;
import com.hp.hpl.jena.tdb.base.file.MetaFile ;
import com.hp.hpl.jena.tdb.base.objectfile.ObjectFile ;
import com.hp.hpl.jena.tdb.index.TupleIndex ;
import com.hp.hpl.jena.tdb.nodetable.NodeTable ;
import com.hp.hpl.jena.tdb.store.DatasetGraphTDB ;
import com.hp.hpl.jena.tdb.store.DatasetPrefixesTDB ;
import com.hp.hpl.jena.tdb.store.NodeId ;
import com.hp.hpl.jena.tdb.store.QuadTable ;
import com.hp.hpl.jena.tdb.store.StoreConfig ;
import com.hp.hpl.jena.tdb.store.TripleTable ;
import com.hp.hpl.jena.tdb.sys.DatasetControl ;
import com.hp.hpl.jena.tdb.sys.DatasetControlMRSW ;
import com.hp.hpl.jena.tdb.sys.FileRef ;
import com.hp.hpl.jena.tdb.sys.Names ;
import com.hp.hpl.jena.tdb.sys.SystemTDB ;

/** This class is the process of building a dataset. */ 

public class DatasetBuilderStd implements DatasetBuilder
{
    private static final Logger log = TDB.logInfo ;

    private BlockMgrBuilder blockMgrBuilder ;
    private ObjectFileBuilder objectFileBuilder ;

    //private IndexBuilder indexBuilder ;
    private RangeIndexBuilder rangeIndexBuilder ;
    
    private NodeTableBuilder nodeTableBuilder ;
    
    private TupleIndexBuilder tupleIndexBuilder ;
    
    private SystemParams params ;

    
    private Map<FileRef, BlockMgr> blockMgrs = new HashMap<FileRef, BlockMgr>() ;
    private Map<FileRef, BufferChannel> bufferChannels = new HashMap<FileRef, BufferChannel>() ;
    private Map<FileRef, NodeTable> nodeTables = new HashMap<FileRef, NodeTable>() ;

    public static DatasetGraphTDB build(Location location)
    {
        DatasetBuilderStd x = new DatasetBuilderStd() ;
        x.setStd() ;
        return x.build(location, null) ;
    }
    
    public static DatasetGraphTDB build()
    {
        DatasetBuilderStd x = new DatasetBuilderStd() ;
        x.setStd() ;
        return x.build(Location.mem(), null) ;
    }
    
    public static DatasetBuilderStd stdBuilder()
    {
        DatasetBuilderStd x = new DatasetBuilderStd() ;
        x.setStd() ;
        return x ; 
    }
    
    protected DatasetBuilderStd() {}
    
    public DatasetBuilderStd(BlockMgrBuilder blockMgrBuilder,
                             NodeTableBuilder nodeTableBuilder)
    {
        set(blockMgrBuilder, nodeTableBuilder) ;
    }
    
    // Dalayed initialization
    protected void setAll(NodeTableBuilder nodeTableBuilder,
                          TupleIndexBuilder tupleIndexBuilder,
                          IndexBuilder indexBuilder,
                          RangeIndexBuilder rangeIndexBuilder,
                          BlockMgrBuilder blockMgrBuilder,
                          ObjectFileBuilder objectFileBuilder)
    {
        this.nodeTableBuilder = nodeTableBuilder ;
        this.tupleIndexBuilder = tupleIndexBuilder ;
        //this.indexBuilder = indexBuilder ;
        this.rangeIndexBuilder = rangeIndexBuilder ;
        this.blockMgrBuilder = blockMgrBuilder ;
        this.objectFileBuilder = objectFileBuilder ;
    }
    
    protected void set(NodeTableBuilder nodeTableBuilder,
                       TupleIndexBuilder tupleIndexBuilder,
                       //IndexBuilder indexBuilder,
                       RangeIndexBuilder rangeIndexBuilder)
    {
        this.nodeTableBuilder = nodeTableBuilder ;
        this.tupleIndexBuilder = tupleIndexBuilder ;
        //this.indexBuilder = indexBuilder ;
        this.rangeIndexBuilder = rangeIndexBuilder ;
    }
    
    protected void set(BlockMgrBuilder blockMgrBuilder,
                       NodeTableBuilder nodeTableBuilder)
    {
        Recorder recorder = new Recorder(this) ;
        BlockMgrBuilder blockMgrBuilderRec = new BlockMgrBuilderRecorder(blockMgrBuilder, recorder) ;

        IndexBuilder indexBuilder               = new Builder.IndexBuilderStd(blockMgrBuilderRec, blockMgrBuilderRec) ;
        RangeIndexBuilder rangeIndexBuilder     = new Builder.RangeIndexBuilderStd(blockMgrBuilderRec, blockMgrBuilderRec) ;
        
        this.nodeTableBuilder = nodeTableBuilder ;
        nodeTableBuilder = new NodeTableBuilderRecorder(nodeTableBuilder, recorder) ;
        
        TupleIndexBuilder tupleIndexBuilder     = new Builder.TupleIndexBuilderStd(rangeIndexBuilder) ;
        set(nodeTableBuilder, tupleIndexBuilder, rangeIndexBuilder) ;
    }
        
    protected void setStd()
    {
          ObjectFileBuilder objectFileBuilder     = new Builder.ObjectFileBuilderStd() ;
          BlockMgrBuilder blockMgrBuilder         = new Builder.BlockMgrBuilderStd() ;
          IndexBuilder indexBuilderNT             = new Builder.IndexBuilderStd(blockMgrBuilder, blockMgrBuilder) ;
          NodeTableBuilder nodeTableBuilder       = new Builder.NodeTableBuilderStd(indexBuilderNT, objectFileBuilder) ;
          
          set(blockMgrBuilder, nodeTableBuilder) ;
//        ObjectFileBuilder objectFileBuilder     = new Builder.ObjectFileBuilderStd() ;
//        // Depends on memory/file?
//        BlockMgrBuilder blockMgrBuilder         = new Builder.BlockMgrBuilderStd() ;
//        
//        IndexBuilder indexBuilder               = new Builder.IndexBuilderStd(blockMgrBuilder, blockMgrBuilder) ;
//        RangeIndexBuilder rangeIndexBuilder     = new Builder.RangeIndexBuilderStd(blockMgrBuilder, blockMgrBuilder) ;
//        
//        NodeTableBuilder nodeTableBuilder       = new Builder.NodeTableBuilderStd(indexBuilder, objectFileBuilder) ;
//        TupleIndexBuilder tupleIndexBuilder     = new Builder.TupleIndexBuilderStd(rangeIndexBuilder) ;
//        
//        set(nodeTableBuilder, tupleIndexBuilder, 
//            indexBuilder, rangeIndexBuilder, 
//            blockMgrBuilder, objectFileBuilder) ;
        
    }

    @Override
    public DatasetGraphTDB build(Location location, SystemParams params)
    {
        if ( params == null )
            params = SystemParams.getStdSystemParams() ;
        
        // Ensure that there is global synchronization
        synchronized(DatasetBuilderStd.class)
        {
            return _build(location, params) ;
        }
    }
    
    protected DatasetGraphTDB _build(Location location, SystemParams _params)
    {
        params = _params ;
        init(location) ;
        DatasetControl policy = createConcurrencyPolicy() ;
        
        NodeTable nodeTable = makeNodeTable(location, 
                                            params.indexNode2Id, params.indexId2Node,
                                            params.Node2NodeIdCacheSize, params.NodeId2NodeCacheSize, params.NodeMissCacheSize) ;
        
        TripleTable tripleTable = makeTripleTable(location, nodeTable, policy) ; 
        QuadTable quadTable = makeQuadTable(location, nodeTable, policy) ;
        DatasetPrefixesTDB prefixes = makePrefixTable(location, policy) ;
        ReorderTransformation transform  = chooseReorderTransformation(location) ;
        
        StoreConfig storeConfig = new StoreConfig(location, params, blockMgrs, bufferChannels, nodeTables) ;
        DatasetGraphTDB dsg = new DatasetGraphTDB(tripleTable, quadTable, prefixes, transform, storeConfig) ;
        return dsg ;
    }
    
    protected DatasetControl createConcurrencyPolicy() { return new DatasetControlMRSW() ; }
    
    protected void init(Location location)
    {
        // Build params.
    }
    
    // ==== TODO makeNodeTupleTable.
    
    // ======== Dataset level
    protected TripleTable makeTripleTable(Location location, NodeTable nodeTable, DatasetControl policy)
    {    
        MetaFile metafile = location.getMetaFile() ;
        String dftPrimary = params.primaryIndexTriples ;
        String[] dftIndexes = params.tripleIndexes ;
        
        String primary = metafile.getOrSetDefault("tdb.indexes.triples.primary", dftPrimary) ;
        String x = metafile.getOrSetDefault("tdb.indexes.triples", StrUtils.strjoin(",",dftIndexes)) ;
        String indexes[] = x.split(",") ;
        
        if ( indexes.length != 3 )
            error(log, "Wrong number of triple table indexes: "+StrUtils.strjoin(",", indexes)) ;
        log.debug("Triple table: "+primary+" :: "+StrUtils.strjoin(",", indexes)) ;
        
        TupleIndex tripleIndexes[] = makeTupleIndexes(location, primary, indexes) ;
        
        if ( tripleIndexes.length != indexes.length )
            error(log, "Wrong number of triple table tuples indexes: "+tripleIndexes.length) ;
        TripleTable tripleTable = new TripleTable(tripleIndexes, nodeTable, policy) ;
        metafile.flush() ;
        return tripleTable ;
    }
    
    protected QuadTable makeQuadTable(Location location, NodeTable nodeTable, DatasetControl policy)
    {    
        MetaFile metafile = location.getMetaFile() ;
        String dftPrimary = params.primaryIndexQuads ;
        String[] dftIndexes = params.quadIndexes ;
        
        String primary = metafile.getOrSetDefault("tdb.indexes.quads.primary", dftPrimary) ;
        String x = metafile.getOrSetDefault("tdb.indexes.quads", StrUtils.strjoin(",",dftIndexes)) ;
        String indexes[] = x.split(",") ;

        if ( indexes.length != 6 )
            error(log, "Wrong number of quad table indexes: "+StrUtils.strjoin(",", indexes)) ;
        
        log.debug("Quad table: "+primary+" :: "+StrUtils.strjoin(",", indexes)) ;
        
        TupleIndex quadIndexes[] = makeTupleIndexes(location, primary, indexes) ;
        if ( quadIndexes.length != indexes.length )
            error(log, "Wrong number of quad table tuples indexes: "+quadIndexes.length) ;
        QuadTable quadTable = new QuadTable(quadIndexes, nodeTable, policy) ;
        metafile.flush() ;
        return quadTable ;
    }

    protected DatasetPrefixesTDB makePrefixTable(Location location, DatasetControl policy)
    {    
        /*
         * tdb.prefixes.index.file=prefixIdx
         * tdb.prefixes.indexes=GPU
         * tdb.prefixes.primary=GPU
         * 
         * tdb.prefixes.nodetable.mapping.node2id=prefixes
         * tdb.prefixes.nodetable.mapping.id2node=id2prefix
    
         * 
         * Logical:
         * 
         * tdb.prefixes.index.file=prefixIdx
         * tdb.prefixes.index=GPU
         * tdb.prefixes.nodetable.mapping.node2id=prefixes
         * tdb.prefixes.nodetable.mapping.id2node=id2prefix
    
         * 
         * Physical:
         * 
         * It's a node table and an index (rangeindex)
         * 
         */

        // Some of this is also in locationMetadata.
        
        MetaFile metafile = location.getMetaFile() ;
        String dftPrimary = params.primaryIndexPrefix ;
        String[] dftIndexes = params.prefixIndexes ;
        
        // The index using for Graph+Prefix => URI
        String indexPrefixes = metafile.getOrSetDefault("tdb.prefixes.index.file", params.indexPrefix) ;
        String primary = metafile.getOrSetDefault("tdb.prefixes.primary", dftPrimary) ;
        String x = metafile.getOrSetDefault("tdb.prefixes.indexes", StrUtils.strjoin(",",dftIndexes)) ;
        String indexes[] = x.split(",") ;
        
        TupleIndex prefixIndexes[] = makeTupleIndexes(location, primary, indexes, new String[]{indexPrefixes}) ;
        if ( prefixIndexes.length != indexes.length )
            error(log, "Wrong number of triple table tuples indexes: "+prefixIndexes.length) ;
        
        // The nodetable.
        String pnNode2Id = metafile.getOrSetDefault("tdb.prefixes.nodetable.mapping.node2id", params.prefixNode2Id) ;
        String pnId2Node = metafile.getOrSetDefault("tdb.prefixes.nodetable.mapping.id2node", params.prefixId2Node) ;
        
        // No cache - the prefix mapping is a cache
        NodeTable prefixNodes = makeNodeTable(location, pnNode2Id, pnId2Node, -1, -1, -1)  ;
        
        DatasetPrefixesTDB prefixes = new DatasetPrefixesTDB(prefixIndexes, prefixNodes, policy) ; 
        
        log.debug("Prefixes: "+x) ;
        
        return prefixes ;
    }

    
    protected ReorderTransformation chooseReorderTransformation(Location location)
    {    
        return chooseOptimizer(location) ;
    }

    // ======== Components level
    
//    // This is not actually used in main dataset builder because it's done inside TripleTable/QuadTable.
//    protected NodeTupleTable makeNodeTupleTable(Location location, String primary, String[] indexes, NodeTable nodeTable, ConcurrencyPolicy policy)
//    {    
//        int N = indexes.length ;
//        TupleIndex tripleIndexes[] = makeTupleIndexes(location, primary, indexes) ;
//        if ( tripleIndexes.length != indexes.length )
//            error(log, "Wrong number of node table tuples indexes: expected="+N+" : actual="+tripleIndexes.length) ;
//        NodeTupleTable ntt = new NodeTupleTableConcrete(N, tripleIndexes, nodeTable, policy) ;
//        return ntt ;
//    }
    
    private TupleIndex[] makeTupleIndexes(Location location, String primary, String[] indexNames)
    {
        return makeTupleIndexes(location, primary, indexNames, indexNames) ;
    }
    
    private TupleIndex[] makeTupleIndexes(Location location, String primary, String[] indexNames, String[] filenames)
    {
        if ( primary.length() != 3 && primary.length() != 4 )
            error(log, "Bad primary key length: "+primary.length()) ;
    
        int indexRecordLen = primary.length()*NodeId.SIZE ;
        TupleIndex indexes[] = new TupleIndex[indexNames.length] ;
        for (int i = 0 ; i < indexes.length ; i++)
            indexes[i] = makeTupleIndex(location, filenames[i], primary, indexNames[i]) ;
        return indexes ;
    }

    // ----
    protected TupleIndex makeTupleIndex(Location location, String name, String primary, String indexOrder)
    {
        // Commonly,  name == indexOrder.
        // FileSet
        FileSet fs = new FileSet(location, name) ;
        ColumnMap colMap = new ColumnMap(primary, indexOrder) ;
        return tupleIndexBuilder.buildTupleIndex(fs, colMap) ;
    }

    // ----
    
    protected NodeTable makeNodeTable(Location location, String indexNode2Id, String indexId2Node, 
                                      int sizeNode2NodeIdCache, int sizeNodeId2NodeCache, int sizeNodeMissCache)
    {
        /* Physical
         * ---- An object file
         * tdb.file.type=object
         * tdb.file.impl=dat
         * tdb.file.impl.version=dat-v1
         *
         * tdb.object.encoding=sse 
         */
        
        FileSet fsNodeToId = new FileSet(location, indexNode2Id) ;
        FileSet fsId2Node = new FileSet(location, indexId2Node) ;
    
        MetaFile metafile = fsId2Node.getMetaFile() ;
        metafile.checkOrSetMetadata("tdb.file.type", ObjectFile.type) ;
        metafile.checkOrSetMetadata("tdb.file.impl", "dat") ;
        metafile.checkOrSetMetadata("tdb.file.impl.version", "dat-v1") ;
        metafile.checkOrSetMetadata("tdb.object.encoding", "sse") ;
        
        NodeTable nt = nodeTableBuilder.buildNodeTable(fsNodeToId, fsId2Node, sizeNode2NodeIdCache, sizeNodeId2NodeCache, sizeNodeMissCache) ;
        fsNodeToId.getMetaFile().flush() ;
        fsId2Node.getMetaFile().flush() ;
        return nt ;
    }

    private static void error(Logger log, String msg)
    {
        if ( log != null )
            log.error(msg) ;
        throw new TDBException(msg) ;
    }
    
    private static int parseInt(String str, String messageBase)
    {
        try { return Integer.parseInt(str) ; }
        catch (NumberFormatException ex) { error(log, messageBase+": "+str) ; return -1 ; }
    }
    

    /** Set the global flag that control the "No BGP optimizer" warning.
     * Set to false to silence the warning
     */
    public static void setOptimizerWarningFlag(boolean b) { warnAboutOptimizer = b ; }
    private static boolean warnAboutOptimizer = true ;

    public static ReorderTransformation chooseOptimizer(Location location)
    {
        if ( location == null )
            return ReorderLib.identity() ;

        ReorderTransformation reorder = null ;
        if ( location.exists(Names.optStats) )
        {
            try {
                reorder = ReorderLib.weighted(location.getPath(Names.optStats)) ;
                log.debug("Statistics-based BGP optimizer") ;  
            } catch (SSEParseException ex) { 
                log.warn("Error in stats file: "+ex.getMessage()) ;
                reorder = null ;
            }
        }

        if ( reorder == null && location.exists(Names.optFixed) )
        {
            // Not as good but better than nothing.
            reorder = ReorderLib.fixed() ;
            log.debug("Fixed pattern BGP optimizer") ;  
        }

        if ( location.exists(Names.optNone) )
        {
            reorder = ReorderLib.identity() ;
            log.debug("Optimizer explicitly turned off") ;
        }

        if ( reorder == null )
            reorder = SystemTDB.defaultOptimizer ;

        if ( reorder == null && warnAboutOptimizer )
            ARQ.getExecLogger().warn("No BGP optimizer") ;

        return reorder ; 
    }


    interface RecordBlockMgr 
    {
        void record(FileRef fileRef, BlockMgr blockMgr) ;
    }
    
    interface RecordNodeTable 
    {
        void record(FileRef fileRef, NodeTable nodeTable) ;
    }

    static class NodeTableBuilderRecorder implements NodeTableBuilder
    {
        private NodeTableBuilder builder ;
        private RecordNodeTable recorder ;

        NodeTableBuilderRecorder(NodeTableBuilder ntb, RecordNodeTable recorder)
        {
            this.builder = ntb ;
            this.recorder = recorder ;
        }
        
        @Override
        public NodeTable buildNodeTable(FileSet fsIndex, FileSet fsObjectFile, int sizeNode2NodeIdCache,
                                        int sizeNodeId2NodeCache, int sizeNodeMissCacheSize)
        {
            NodeTable nt = builder.buildNodeTable(fsIndex, fsObjectFile, sizeNode2NodeIdCache, sizeNodeId2NodeCache, sizeNodeMissCacheSize) ;
            // It just knows, right?
            FileRef ref = FileRef.create(fsObjectFile.filename(Names.extNodeData)) ;
            recorder.record(ref, nt) ;
            return nt ;
        }
        
    }
    
    static class BlockMgrBuilderRecorder implements BlockMgrBuilder
    {
        private BlockMgrBuilder builder ;
        private RecordBlockMgr recorder ;

        BlockMgrBuilderRecorder(BlockMgrBuilder blkMgrBuilder, RecordBlockMgr recorder)
        {
            this.builder = blkMgrBuilder ;
            this.recorder = recorder ;
        }
        
        @Override
        public BlockMgr buildBlockMgr(FileSet fileSet, String ext, int blockSize)
        {
            BlockMgr blkMgr = builder.buildBlockMgr(fileSet, ext, blockSize) ;
            FileRef ref = FileRef.create(fileSet, ext) ;
            recorder.record(ref, blkMgr) ;
            return blkMgr ;
        }
    }
    
    static class Recorder implements RecordBlockMgr, RecordNodeTable
    {

        private DatasetBuilderStd dsBuilder ;

        Recorder(DatasetBuilderStd dsBuilder)
        {
            this.dsBuilder = dsBuilder ;
        }
        
        @Override
        public void record(FileRef fileRef, BlockMgr blockMgr)
        {
            //log.info("BlockMgr: "+fileRef) ;
            dsBuilder.blockMgrs.put(fileRef, blockMgr) ;
        }

        @Override
        public void record(FileRef fileRef, NodeTable nodeTable)
        {
            //log.info("NodeTable: "+fileRef) ;
            dsBuilder.nodeTables.put(fileRef, nodeTable) ;
        }
    }
}
