/*
 * Copyright (C) 2018 Southern Illinois University Carbondale, SoftSearch Lab
 *
 * Author: Amiangshu Bosu
 *
 * Licensed under GNU LESSER GENERAL PUBLIC LICENSE Version 3, 29 June 2007
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.gnu.org/licenses/lgpl.html
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package edu.siu.sentise.preprocessing;

import java.util.ArrayList;
import java.util.HashSet;

import edu.siu.sentise.model.SentimentData;

public class ExclamationHandler implements TextPreprocessor {
	
	public  ArrayList<SentimentData> apply(ArrayList<SentimentData> sentiList) {
		for (int i = 0; i < sentiList.size(); i++)
			sentiList.get(i).setText(replacePunctuations(sentiList.get(i).getText()));
		return sentiList;
	}

	private  String replacePunctuations(String text) {

		text=text.replaceAll("!", " exclamationmark ");
		return text;
	}
		
}
