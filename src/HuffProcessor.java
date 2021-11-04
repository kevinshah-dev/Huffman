import javax.swing.tree.TreeNode;
import java.util.PriorityQueue;

/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 *
 * @author Owen Astrachan
 */

public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD);
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;

	private final int myDebugLevel;

	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;

	public HuffProcessor() {
		this(0);
	}

	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}


	public void compress(BitInputStream in, BitOutputStream out){
		int[] counts = readForCounts(in);
		HuffNode root = makeTreeFromCounts(counts);
		String[] codings = makeCodingsFromTree(root);

		out.writeBits(BITS_PER_INT, HUFF_TREE);
		writeHeader(root, out);

		in.reset();
		writeCompressedBits(codings, in, out);
		out.close();
	}



	private void writeCompressedBits(String[] codings, BitInputStream in, BitOutputStream out) {

		while(true){
			int temp = in.readBits(BITS_PER_WORD);
			if (temp == -1) break;
			String code = codings[temp];
			out.writeBits(code.length(), Integer.parseInt(code,2));
			if (myDebugLevel >= DEBUG_HIGH){
				System.out.printf("encoding for %d is %s\n", temp, code);
			}
		}
		String code = codings[PSEUDO_EOF];
		out.writeBits(code.length(), Integer.parseInt(code, 2));
	}

	private void writeHeader(HuffNode root, BitOutputStream out) {
		if((root.myRight!=null||root.myLeft!=null)){
			out.writeBits(1,0);
			writeHeader(root.myLeft, out);
			writeHeader(root.myRight, out);
		}
		else{
			out.writeBits(1,1);
			out.writeBits(BITS_PER_WORD+1, root.myValue);
		}
	}


	private HuffNode makeTreeFromCounts(int[] counts) {
		PriorityQueue<HuffNode> pq = new PriorityQueue<>();

		if (myDebugLevel >= DEBUG_HIGH){
			for(int i = 0; i < ALPH_SIZE; i++){
				if (counts[i] != 0){
					System.out.printf("%d\t%d\n", i, counts[i]);
				}
			}
		}

		for (int i = 0; i < counts.length; i++) {
			if (counts[i] > 0){
				pq.add(new HuffNode(i, counts[i], null, null));
			}
		}

		while (pq.size() > 1){
			HuffNode left = pq.remove();
			HuffNode right = pq.remove();
			HuffNode t = new HuffNode(0, left.myWeight + right.myWeight, left, right);
			pq.add(t);
		}
		HuffNode root = pq.remove();
		return root;
	}


	private String[] makeCodingsFromTree(HuffNode root) {
		String [] encodings = new String[ALPH_SIZE + 1];
		codingHelper(root, "", encodings);
		return encodings;
	}


	private void codingHelper(HuffNode root, String path, String[] encodings){
		if(root.myLeft==null&&root.myRight==null){
			encodings[root.myValue] = path;
			return;
		}
		codingHelper(root.myLeft, path + "0", encodings);
		codingHelper(root.myRight, path + "1", encodings);
	}


	private int[] readForCounts(BitInputStream in) {
		int [] freq = new int [ALPH_SIZE+1];
		while(true){
			int bits = in.readBits(BITS_PER_WORD);
			if (bits == -1) break;
			freq[bits]+=1;
		}

		freq[PSEUDO_EOF] = 1;

		return freq;
	}

	public void decompress(BitInputStream in, BitOutputStream out){

		int magic = in.readBits(BITS_PER_INT);
		if (magic != HUFF_TREE) {
			throw new HuffException("invalid magic number "+magic);
		}
		HuffNode root = readTree(in);
		HuffNode current = root;
		while (true){
			System.out.print("Traversing a node");
			int bits = in.readBits(1);
			if (bits == -1) throw new HuffException("bad input, no PSEUDO_EOF");
			else {
				if (bits == 0) current = current.myLeft;
				else current = current.myRight;

				if (current.myLeft == null && current.myRight == null){
					if (current.myValue == PSEUDO_EOF) {
						System.out.println("Reached EOS");
						break;
					}
					else {
						out.writeBits(BITS_PER_WORD, current.myValue);
						current = root;
					}
				}

			}
		}
		out.close();
	}


	private HuffNode readTree(BitInputStream in) {
		int bit = in.readBits(1);
		if (bit == -1) throw new HuffException("reading bits failed");
		if (bit == 0) {
			HuffNode left = readTree(in);
			HuffNode right = readTree(in);
			return new HuffNode(0,0,left,right);

		}
		else{
			int value = in.readBits(BITS_PER_WORD+1);
			return new HuffNode(value, 0, null, null);
		}
	}

}
